package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.ActivityRecipeDetailBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var recipeId: String? = null
    private var currentRecipe: Receta? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, CookingModeActivity::class.java)
            intent.putExtra("RECIPE_ID", recipeId)
            startActivity(intent)
        }

        setupTabs()
        loadRecipeData()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateList(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadRecipeData() {
        recipeId?.let { id ->
            lifecycleScope.launch {
                try {
                    val recipe = repository.getRecetaById(id)
                    recipe?.let {
                        currentRecipe = it
                        binding.tvTitle.text = it.nombre
                        binding.tvCategory.text = it.categoria
                        binding.tvTime.text = "${it.tiempoEstimado} min"
                        binding.tvDifficulty.text = it.dificultad
                        binding.tvLikes.text = it.totalLikes.toString()
                        binding.tvDescription.text = it.descripcion

                        // Load author data
                        val author = repository.getUsuarioByUid(it.autorUid)
                        author?.let { user ->
                            binding.tvAuthorName.text = user.nombreCompleto
                            binding.tvAuthorHandle.text = "@${user.nombreUsuario}"
                        }

                        updateList(0) // Show ingredients by default
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    // Adapters
    inner class IngredientAdapter(private val list: List<IngredienteReceta>) : RecyclerView.Adapter<IngredientAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            android.view.View.inflate(parent.context, android.R.layout.simple_list_item_2, null)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            // Simple display for demo
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    inner class StepAdapter(private val list: List<PasoReceta>) : RecyclerView.Adapter<StepAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            android.view.View.inflate(parent.context, android.R.layout.simple_list_item_2, null)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            // Simple display for demo
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
