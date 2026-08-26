package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.ActivityRecipeDetailBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import coil.load
import io.github.jan.supabase.auth.auth
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var recipeId: String? = null
    private var currentRecipe: Receta? = null
    private var followState = "ninguno" // "ninguno", "pendiente", "siguiendo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        recipeId = intent.getStringExtra("RECIPE_ID")
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.toolbar.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(this, CreateRecipeActivity::class.java)
                    intent.putExtra("RECIPE_ID", recipeId)
                    startActivity(intent)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true
                }
                R.id.action_share -> {
                    lifecycleScope.launch {
                        val recipe = currentRecipe ?: return@launch
                        val ingredients = repository.getIngredientes(recipeId ?: "")
                        val steps = repository.getPasos(recipeId ?: "")
                        val images = repository.getImagenesReceta(recipeId ?: "")
                        val imageUrl = if (images.isNotEmpty()) images[0] else null
                        
                        ShareManager.shareRecipe(
                            context = this@RecipeDetailActivity,
                            recipe = recipe,
                            ingredients = ingredients,
                            steps = steps,
                            imageUrl = imageUrl
                        )
                    }
                    true
                }
                R.id.action_report -> {
                    val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return@setOnMenuItemClickListener false
                    lifecycleScope.launch {
                        val success = repository.reportarRecurso(uid, recipeId ?: "", ResourceTypes.RECIPE, "Contenido inapropiado")
                        if (success) Toast.makeText(this@RecipeDetailActivity, "Reporte enviado", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_block -> {
                    val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return@setOnMenuItemClickListener false
                    val authorUid = currentRecipe?.autorUid ?: return@setOnMenuItemClickListener false
                    lifecycleScope.launch {
                        val success = repository.bloquearUsuario(uid, authorUid)
                        if (success) {
                            Toast.makeText(this@RecipeDetailActivity, "Usuario bloqueado", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, CookingModeActivity::class.java)
            intent.putExtra("RECIPE_ID", recipeId)
            startActivity(intent)
        }

        binding.btnFollow.setOnClickListener { toggleFollow() }

        binding.btnSendMessage.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("OTHER_USER_ID", currentRecipe?.autorUid)
            intent.putExtra("RECIPE_ID", recipeId)
            intent.putExtra("RECIPE_NAME", currentRecipe?.nombre)
            startActivity(intent)
        }

        binding.tvLikes.setOnClickListener {
            val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
            binding.tvLikes.isEnabled = false
            lifecycleScope.launch {
                try {
                    repository.toggleLike(currentUid, recipeId ?: "", ResourceTypes.RECIPE)
                    
                    // Contar likes directamente para mayor precisión
                    val realCount = repository.getLikeCount(recipeId ?: "")
                    withContext(Dispatchers.Main) {
                        binding.tvLikes.text = realCount.toString()
                        updateLikeUI()
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al dar like", e)
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.tvLikes.isEnabled = true
                    }
                }
            }
        }

        binding.ivSave.setOnClickListener {
            val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    repository.toggleSave(uid, recipeId ?: "", ResourceTypes.RECIPE)
                    updateSaveUI()
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al guardar", e)
                }
            }
        }

        setupTabs()
        loadRecipeData()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateList(tab?.position ?: 0) }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadRecipeData() {
        recipeId?.let { id ->
            lifecycleScope.launch {
                try {
                    val recipe = repository.getRecetaById(id) ?: return@launch
                    currentRecipe = recipe
                    binding.tvTitle.text = recipe.nombre
                    binding.tvCategory.text = recipe.categoria ?: "General"
                    binding.tvTime.text = "${recipe.tiempoEstimado} min"
                    binding.tvDifficulty.text = recipe.dificultad?.uppercase()
                    
                    // Cargar contador real de likes
                    val realLikes = repository.getLikeCount(id)
                    binding.tvLikes.text = realLikes.toString()
                    
                    binding.tvDescription.text = recipe.descripcion

                    val images = repository.getImagenesReceta(id)
                    if (images.isNotEmpty()) binding.ivRecipeDetail.load(images[0])

                    val author = repository.getUsuarioByUid(recipe.autorUid)
                    author?.let { user ->
                        binding.tvAuthorName.text = user.nombreCompleto ?: user.nombreUsuario
                        binding.tvAuthorHandle.text = "@${user.nombreUsuario}"
                        user.fotoUrl?.let { binding.ivAuthorAvatar.load(it) }
                        
                        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                        if (currentUid == user.uid) {
                            binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible = true
                            binding.toolbar.menu.findItem(R.id.action_delete)?.isVisible = true
                            binding.btnSendMessage.visibility = View.GONE
                        }
                        
                        checkFollowStatus(user.uid ?: "")
                    }
                    
                    updateSaveUI()
                    updateLikeUI()
                    updateList(0)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun updateLikeUI() {
        val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        lifecycleScope.launch {
            val likedIds = repository.getLikedResourceIds(uid)
            val isLiked = likedIds.contains(recipeId ?: "")
            if (isLiked) {
                binding.tvLikes.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart, 0, 0, 0)
                binding.tvLikes.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
            } else {
                binding.tvLikes.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart, 0, 0, 0)
                binding.tvLikes.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_secondary))
            }
        }
    }

    private fun updateSaveUI() {
        val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        lifecycleScope.launch {
            val saved = repository.isSaved(uid, recipeId ?: "")
            binding.ivSave.setImageResource(if (saved) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
        }
    }

    private fun checkFollowStatus(authorUid: String) {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        if (currentUid == authorUid) {
            binding.btnFollow.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            followState = repository.getEstadoSeguimiento(currentUid, authorUid)
            updateFollowButton()
        }
    }

    private fun updateFollowButton() {
        when (followState) {
            "siguiendo" -> {
                binding.btnFollow.text = "Siguiendo"
                binding.btnFollow.alpha = 0.6f
                binding.btnFollow.isEnabled = true
            }
            "pendiente" -> {
                binding.btnFollow.text = "Solicitud enviada"
                binding.btnFollow.alpha = 0.6f
                binding.btnFollow.isEnabled = false
            }
            else -> {
                binding.btnFollow.text = "Seguir"
                binding.btnFollow.alpha = 1.0f
                binding.btnFollow.isEnabled = true
            }
        }
    }

    private fun toggleFollow() {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        val authorUid = currentRecipe?.autorUid ?: return
        lifecycleScope.launch {
            try {
                if (followState == "siguiendo") {
                    repository.unfollowUser(currentUid, authorUid)
                } else if (followState == "ninguno") {
                    val success = repository.followUser(currentUid, authorUid)
                    if (!success) {
                        Toast.makeText(this@RecipeDetailActivity, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                    }
                }
                followState = repository.getEstadoSeguimiento(currentUid, authorUid)
                updateFollowButton()
                
                // OPCIONAL: Podrías recargar los datos del autor aquí si mostraras 
                // sus seguidores en esta pantalla. Como no están en el layout actual,
                // con actualizar el botón basta.
            } catch (e: Exception) {
                Toast.makeText(this@RecipeDetailActivity, "Error al actualizar seguimiento", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Eliminar receta")
            .setMessage("¿Estás seguro de que deseas eliminar esta receta? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteRecipe()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteRecipe() {
        val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        val id = recipeId ?: return
        lifecycleScope.launch {
            val success = repository.deleteReceta(id, uid)
            if (success) {
                Toast.makeText(this@RecipeDetailActivity, "Receta eliminada", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@RecipeDetailActivity, "Error al eliminar receta", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateList(position: Int) {
        recipeId?.let { id ->
            lifecycleScope.launch {
                try {
                    if (position == 0) {
                        val ingredients = repository.getIngredientes(id)
                        Log.d("RecetApp", "Detalle - Ingredientes: ${ingredients.size}")
                        binding.rvContent.adapter = IngredientAdapter(ingredients)
                    } else {
                        val steps = repository.getPasos(id)
                        Log.d("RecetApp", "Detalle - Pasos: ${steps.size}")
                        binding.rvContent.adapter = StepAdapter(steps)
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al cargar lista en detalle", e)
                }
            }
        }
    }

    inner class IngredientAdapter(private val list: List<IngredienteReceta>) : RecyclerView.Adapter<IngredientAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
            LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_2, p, false)
        )
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.itemView.findViewById<TextView>(android.R.id.text1).apply {
                text = "• ${item.nombre}"
                setTextColor(getColor(R.color.text_primary))
                textSize = 18f
            }
            h.itemView.findViewById<TextView>(android.R.id.text2).apply {
                text = "${item.cantidad ?: ""} ${item.unit_text ?: item.unidad ?: ""}"
                setTextColor(getColor(R.color.orange_primary))
                setPadding(32, 0, 0, 0)
                textSize = 14f
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }

    // Helper property to handle different naming in DB vs Model if needed, 
    // but here we just rely on 'unidad' from IngredienteReceta
    private val IngredienteReceta.unit_text: String? get() = unidad

    inner class StepAdapter(private val list: List<PasoReceta>) : RecyclerView.Adapter<StepAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
            LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_2, p, false)
        )
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.itemView.findViewById<TextView>(android.R.id.text1).apply {
                text = "PASO ${item.orden}"
                setTextColor(getColor(R.color.orange_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 14f
            }
            h.itemView.findViewById<TextView>(android.R.id.text2).apply {
                text = item.descripcion
                setTextColor(getColor(R.color.text_primary))
                textSize = 17f
                setPadding(0, 4, 0, 16)
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }

}
