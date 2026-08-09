package com.example.recetapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class CreateRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var currentStep = 0
    private val steps = listOf("Información", "Ingredientes", "Pasos", "Imagen y visibilidad")

    // Recipe Data
    private var recipeName = ""
    private var estimatedTime = ""
    private var recipeVisibility = "publica"
    private val ingredientsList = mutableListOf(IngredienteReceta(recetaId = "", nombre = ""))
    private val stepsList = mutableListOf(PasoReceta(recetaId = "", orden = 1, descripcion = ""))
    private var selectedImageUri: Uri? = null
    
    private var isSaving = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            renderStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // 1. Insert Receta
                val newReceta = Receta(
                    autorUid = userId,
                    nombre = recipeName,
                    descripcion = "Receta creada desde Android",
                    categoria = "General",
                    dificultad = "media",
                    tiempoEstimado = estimatedTime.toIntOrNull() ?: 0,
                    visibilidad = recipeVisibility
                )
                
                val inserted = repository.insertReceta(newReceta)
                
                if (inserted?.id != null) {
                    val recetaId = inserted.id
                    
                    // 2. Insert Ingredientes
                    val finalIngredients = ingredientsList
                        .filter { it.nombre.isNotBlank() }
                        .map { it.copy(id = null, recetaId = recetaId) }
                    
                    if (finalIngredients.isNotEmpty()) {
                        repository.insertIngredientes(finalIngredients)
                    }
                    
                    // 3. Insert Pasos
                    val finalSteps = stepsList
                        .filter { it.descripcion.isNotBlank() }
                        .map { it.copy(id = null, recetaId = recetaId) }
                    
                    if (finalSteps.isNotEmpty()) {
                        repository.insertPasos(finalSteps)
                    }

                    // 4. Upload Image
                    selectedImageUri?.let { uri ->
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val fileName = "recipe_${recetaId}.jpg"
                            val imageUrl = repository.uploadImage("recipes", fileName, bytes)
                            if (imageUrl != null) {
                                repository.insertImagenReceta(recetaId, imageUrl)
                            }
                        }
                    }
                    
                    ToastManager.showToast(this@CreateRecipeActivity, "¡Receta publicada!")
                    finish()
                }
            } catch (e: Exception) {
                isSaving = false
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Publicar receta"
                e.printStackTrace()
                ToastManager.showToast(this@CreateRecipeActivity, "Error: ${e.message}", isLong = true)
            }
        }
    }

    private fun renderStep() {
        hideSystemUI()
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
        binding.btnContinue.text = if (currentStep == 3) "Publicar receta" else "Continuar"
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
        b.cardRecipeImage.setOnClickListener { pickImage.launch("image/*") }
        selectedImageUri?.let { b.ivRecipePreview.setImageURI(it) }
        
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

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
