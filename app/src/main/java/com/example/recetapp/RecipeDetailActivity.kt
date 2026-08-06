package com.example.recetapp

import android.content.Intent
import android.os.Bundle
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
import coil.load
import io.github.jan.supabase.auth.auth

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var recipeId: String? = null
    private var currentRecipe: Receta? = null
    private var isFollowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, CookingModeActivity::class.java)
            intent.putExtra("RECIPE_ID", recipeId)
            startActivity(intent)
        }

        binding.btnFollow.setOnClickListener { toggleFollow() }

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
                    binding.tvLikes.text = recipe.totalLikes.toString()
                    binding.tvDescription.text = recipe.descripcion

                    val images = repository.getImagenesReceta(id)
                    if (images.isNotEmpty()) binding.ivRecipeDetail.load(images[0])

                    val author = repository.getUsuarioByUid(recipe.autorUid)
                    author?.let { user ->
                        binding.tvAuthorName.text = user.nombreCompleto ?: user.nombreUsuario
                        binding.tvAuthorHandle.text = "@${user.nombreUsuario}"
                        user.fotoUrl?.let { binding.ivAuthorAvatar.load(it) }
                        checkFollowStatus(user.uid ?: "")
                    }

                    updateList(0)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun checkFollowStatus(authorUid: String) {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        if (currentUid == authorUid) {
            binding.btnFollow.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            isFollowing = repository.isFollowing(currentUid, authorUid)
            updateFollowButton()
        }
    }

    private fun updateFollowButton() {
        binding.btnFollow.text = if (isFollowing) "Siguiendo" else "Seguir"
        binding.btnFollow.alpha = if (isFollowing) 0.6f else 1.0f
    }

    private fun toggleFollow() {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        val authorUid = currentRecipe?.autorUid ?: return
        lifecycleScope.launch {
            try {
                if (isFollowing) repository.unfollowUser(currentUid, authorUid)
                else repository.followUser(currentUid, authorUid)
                isFollowing = !isFollowing
                updateFollowButton()
            } catch (e: Exception) { 
                Toast.makeText(this@RecipeDetailActivity, "Error al actualizar seguimiento", Toast.LENGTH_SHORT).show() 
            }
        }
    }

    private fun updateList(position: Int) {
        recipeId?.let { id ->
            lifecycleScope.launch {
                if (position == 0) {
                    val ingredients = repository.getIngredientes(id)
                    binding.rvContent.adapter = IngredientAdapter(ingredients)
                } else {
                    val steps = repository.getPasos(id)
                    binding.rvContent.adapter = StepAdapter(steps)
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

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
