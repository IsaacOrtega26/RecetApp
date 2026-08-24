package com.example.recetapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreateRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var currentStep = 0
    private val steps = listOf("Información", "Ingredientes", "Pasos", "Imagen y visibilidad")

    // Recipe Data
    private var recipeId: String? = null
    private var recipeName = ""
    private var estimatedTime = ""
    private var recipeVisibility = "publica"
    private var recipeCategory = "General"
    private var recipeDifficulty = "media"
    private val ingredientsList = mutableListOf(IngredienteReceta(recetaId = "", nombre = ""))
    private val stepsList = mutableListOf(PasoReceta(recetaId = "", orden = 1, descripcion = ""))
    private var selectedImageUri: Uri? = null
    private var existingImageUrl: String? = null
    
    private var isSaving = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            renderStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId != null) {
            binding.toolbar.title = "Editar receta"
            loadRecipeData()
        }

        binding.toolbar.setNavigationOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            } else {
                finish()
            }
        }

        binding.btnContinue.setOnClickListener {
            if (isSaving) return@setOnClickListener
            
            if (currentStep < 3) {
                currentStep++
                renderStep()
            } else {
                saveRecipeToSupabase()
            }
        }

        renderStep()
    }

    private fun loadRecipeData() {
        val id = recipeId ?: return
        lifecycleScope.launch {
            try {
                val recipe = repository.getRecetaById(id) ?: return@launch
                recipeName = recipe.nombre
                estimatedTime = recipe.tiempoEstimado?.toString() ?: ""
                recipeVisibility = recipe.visibilidad
                recipeCategory = recipe.categoria ?: "General"
                recipeDifficulty = recipe.dificultad ?: "media"

                val ingredients = repository.getIngredientes(id)
                if (ingredients.isNotEmpty()) {
                    ingredientsList.clear()
                    ingredientsList.addAll(ingredients)
                }

                val stepsData = repository.getPasos(id)
                if (stepsData.isNotEmpty()) {
                    stepsList.clear()
                    stepsList.addAll(stepsData)
                }

                val images = repository.getImagenesReceta(id)
                if (images.isNotEmpty()) {
                    existingImageUrl = images[0]
                }
                
                renderStep()
            } catch (e: Exception) {
                e.printStackTrace()
                ToastManager.showToast(this@CreateRecipeActivity, "Error al cargar receta")
            }
        }
    }

    private fun saveRecipeToSupabase() {
        val userId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        if (recipeName.isBlank()) {
            ToastManager.showToast(this, "El nombre es obligatorio")
            currentStep = 0
            renderStep()
            return
        }

        isSaving = true
        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Publicando..."

        lifecycleScope.launch {
            try {
                // 1. Insert/Update Receta
                val recetaToSave = Receta(
                    id = recipeId,
                    autorUid = userId,
                    nombre = recipeName,
                    descripcion = "Receta creada desde Android",
                    categoria = recipeCategory,
                    dificultad = recipeDifficulty,
                    tiempoEstimado = estimatedTime.toIntOrNull() ?: 0,
                    visibilidad = recipeVisibility
                )
                
                val finalRecetaId: String
                if (recipeId == null) {
                    val inserted = repository.insertReceta(recetaToSave)
                    finalRecetaId = inserted?.id ?: throw Exception("Error al insertar receta")
                } else {
                    repository.updateReceta(recetaToSave)
                    finalRecetaId = recipeId!!
                    // Limpiar datos antiguos para evitar duplicados
                    repository.deleteIngredientes(finalRecetaId)
                    repository.deletePasos(finalRecetaId)
                }
                
                // 2. Insert Ingredientes
                val finalIngredients = ingredientsList
                    .filter { it.nombre.isNotBlank() }
                    .map { it.copy(id = null, recetaId = finalRecetaId) }
                
                if (finalIngredients.isNotEmpty()) {
                    repository.insertIngredientes(finalIngredients)
                }
                
                // 3. Insert Pasos
                val finalSteps = stepsList
                    .filter { it.descripcion.isNotBlank() }
                    .map { it.copy(id = null, recetaId = finalRecetaId) }
                
                if (finalSteps.isNotEmpty()) {
                    repository.insertPasos(finalSteps)
                }

                // 4. Upload Image
                selectedImageUri?.let { uri ->
                    Log.d("RecetApp", "Subiendo imagen para receta $finalRecetaId")
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val fileName = "recipe_${finalRecetaId}.jpg"
                        val imageUrl = repository.uploadImage("recipes", fileName, bytes)
                        if (imageUrl != null) {
                            Log.d("RecetApp", "Imagen subida: $imageUrl")
                            if (recipeId != null) {
                                // Evitar filas duplicadas al reemplazar la imagen de una receta existente
                                repository.deleteImagenesReceta(finalRecetaId)
                            }
                            repository.insertImagenReceta(finalRecetaId, imageUrl)
                        } else {
                            Log.e("RecetApp", "Error al subir imagen al Storage")
                        }
                    }
                }
                
                val msg = if (recipeId == null) "¡Receta publicada!" else "¡Receta actualizada!"
                ToastManager.showToast(this@CreateRecipeActivity, msg)
                finish()
            } catch (e: Exception) {
                isSaving = false
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = if (recipeId == null) "Publicar receta" else "Actualizar receta"
                e.printStackTrace()
                ToastManager.showToast(this@CreateRecipeActivity, "Error: ${e.message}", isLong = true)
            }
        }
    }

    private fun renderStep() {
        binding.fragmentContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        when (currentStep) {
            0 -> renderStepInfo(inflater)
            1 -> renderStepIngredients(inflater)
            2 -> renderStepRecipeSteps(inflater)
            3 -> renderStepVisibility(inflater)
        }

        binding.tvStepInfo.text = "Paso ${currentStep + 1} de 4: ${steps[currentStep]}"
        updateProgressBar()
        binding.btnContinue.text = if (currentStep == 3) {
            if (recipeId == null) "Publicar receta" else "Actualizar receta"
        } else "Continuar"
    }

    private fun updateProgressBar() {
        val active = getColor(R.color.orange_primary)
        val inactive = getColor(R.color.input_background)
        binding.progress1.setBackgroundColor(if (currentStep >= 0) active else inactive)
        binding.progress2.setBackgroundColor(if (currentStep >= 1) active else inactive)
        binding.progress3.setBackgroundColor(if (currentStep >= 2) active else inactive)
        binding.progress4.setBackgroundColor(if (currentStep >= 3) active else inactive)
    }

    private fun renderStepInfo(inflater: LayoutInflater) {
        val b = StepInfoBinding.inflate(inflater, binding.fragmentContainer, true)
        b.etRecipeName.setText(recipeName)
        b.etTime.setText(estimatedTime)

        val categories = listOf("Dulce", "Salado", "Sin gluten", "Vegano", "Postres", "Panadería")
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        b.actvCategory.setAdapter(catAdapter)
        b.actvCategory.setText(recipeCategory, false)

        // Dificultad
        when (recipeDifficulty) {
            "baja" -> b.chipEasy.isChecked = true
            "media" -> b.chipMedium.isChecked = true
            "alta" -> b.chipHigh.isChecked = true
        }

        b.etRecipeName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { recipeName = s.toString() }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
        b.etTime.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { estimatedTime = s.toString() }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
        b.actvCategory.setOnItemClickListener { parent, _, position, _ ->
            recipeCategory = parent.getItemAtPosition(position).toString()
        }
        b.cgDifficulty.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                recipeDifficulty = when (checkedIds[0]) {
                    R.id.chipEasy -> "baja"
                    R.id.chipHigh -> "alta"
                    else -> "media"
                }
            }
        }
    }

    private fun renderStepIngredients(inflater: LayoutInflater) {
        val b = StepIngredientsBinding.inflate(inflater, binding.fragmentContainer, true)
        val adapter = IngredientEditAdapter(ingredientsList)
        b.rvIngredients.adapter = adapter
        b.btnAddIngredient.setOnClickListener {
            ingredientsList.add(IngredienteReceta(recetaId = "", nombre = ""))
            adapter.notifyItemInserted(ingredientsList.size - 1)
        }
    }

    private fun renderStepRecipeSteps(inflater: LayoutInflater) {
        val b = StepRecipeStepsBinding.inflate(inflater, binding.fragmentContainer, true)
        val adapter = StepEditAdapter(stepsList)
        b.rvSteps.adapter = adapter
        b.btnAddStep.setOnClickListener {
            stepsList.add(PasoReceta(recetaId = "", orden = stepsList.size + 1, descripcion = ""))
            adapter.notifyItemInserted(stepsList.size - 1)
        }
    }

    private fun renderStepVisibility(inflater: LayoutInflater) {
        val b = StepVisibilityBinding.inflate(inflater, binding.fragmentContainer, true)
        
        // Cargar vista previa si ya existe (nueva selección)
        if (selectedImageUri != null) {
            b.ivRecipePreview.setImageURI(selectedImageUri)
            b.ivRecipePreview.visibility = View.VISIBLE
        } else if (existingImageUrl != null) {
            // Cargar imagen de Supabase si estamos editando y no hemos elegido una nueva
            b.ivRecipePreview.load(existingImageUrl)
            b.ivRecipePreview.visibility = View.VISIBLE
        }
        
        b.cardRecipeImage.setOnClickListener { pickImage.launch("image/*") }
        
        b.rgVisibility.setOnCheckedChangeListener { _, id ->
            recipeVisibility = when(id) {
                R.id.rbPrivate -> "privada"
                R.id.rbFollowers -> "seguidores"
                else -> "publica"
            }
        }
        
        when(recipeVisibility) {
            "privada" -> b.rbPrivate.isChecked = true
            "seguidores" -> b.rbFollowers.isChecked = true
            else -> b.rbPublic.isChecked = true
        }
    }

    inner class IngredientEditAdapter(private val list: MutableList<IngredienteReceta>) : RecyclerView.Adapter<IngredientEditAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemIngredientBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.binding.etName.setText(item.nombre)
            h.binding.etQty.setText(item.cantidad?.toString() ?: "")
            
            val units = listOf("g", "ml", "kg", "l", "u", "cdta", "cda")
            val uAdapter = ArrayAdapter(h.binding.root.context, android.R.layout.simple_dropdown_item_1line, units)
            h.binding.actvUnit.setAdapter(uAdapter)
            if (item.unidad != null) h.binding.actvUnit.setText(item.unidad, false)

            h.binding.etName.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { list[h.adapterPosition] = list[h.adapterPosition].copy(nombre = s.toString()) }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
            h.binding.etQty.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { list[h.adapterPosition] = list[h.adapterPosition].copy(cantidad = s.toString().toDoubleOrNull()) }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
            h.binding.actvUnit.setOnItemClickListener { parent, _, p, _ ->
                list[h.adapterPosition] = list[h.adapterPosition].copy(unidad = parent.getItemAtPosition(p).toString())
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class StepEditAdapter(private val list: MutableList<PasoReceta>) : RecyclerView.Adapter<StepEditAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemRecipeStepBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.binding.tvStepNumber.text = item.orden.toString()
            h.binding.etStepText.setText(item.descripcion)
            h.binding.etStepText.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { list[h.adapterPosition] = list[h.adapterPosition].copy(descripcion = s.toString()) }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemRecipeStepBinding) : RecyclerView.ViewHolder(binding.root)
    }

}
