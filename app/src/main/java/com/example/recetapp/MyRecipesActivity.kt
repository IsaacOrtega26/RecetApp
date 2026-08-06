package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.ActivityMyRecipesBinding
import com.example.recetapp.databinding.ItemRecipeGridBinding
import kotlinx.coroutines.launch

class MyRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyRecipesBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        binding = ActivityMyRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnAddRecipe.setOnClickListener {
            startActivity(Intent(this, CreateRecipeActivity::class.java))
        }

        loadMyRecipes()
    }

    private fun loadMyRecipes() {
        lifecycleScope.launch {
            try {
                // For demo, we get all recipes or filter by current user if available
                val recipes = repository.getAllRecetas()
                binding.rvMyRecipes.adapter = RecipeAdapter(recipes) { recipe ->
                    val intent = Intent(this@MyRecipesActivity, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", recipe.id)
                    startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class RecipeAdapter(
        private val list: List<Receta>,
        private val onClick: (Receta) -> Unit
    ) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemRecipeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.binding.tvTitle.text = item.nombre
            // Image loading would go here
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size

        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
