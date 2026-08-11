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
import com.google.android.material.tabs.TabLayout
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MyRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyRecipesBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnAddRecipe.setOnClickListener {
            startActivity(Intent(this, CreateRecipeActivity::class.java))
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                loadRecipes(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadRecipes(0)
    }

    private fun loadRecipes(tabPosition: Int) {
        val uid = currentUserId ?: return
        lifecycleScope.launch {
            try {
                val recipes = when (tabPosition) {
                    0 -> repository.getRecetasByAutor(uid)
                    1 -> repository.getRecetasGuardadas(uid)
                    else -> emptyList() // "Compartidas" placeholder
                }
                
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

}
