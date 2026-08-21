package com.example.recetapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.ActivityAdminPanelBinding
import com.example.recetapp.databinding.ItemRecipeGridBinding
import kotlinx.coroutines.launch

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupAdminContent()
    }

    private fun setupAdminContent() {
        lifecycleScope.launch {
            try {
                Log.d("RecetApp", "AdminPanel: Cargando datos...")
                // Estadísticas
                val allRecipes = repository.getAllRecetas()
                val totalUsers = repository.getTotalUsersCount()
                val reports = repository.getReportes()
                
                Log.d("RecetApp", "AdminPanel: Recetas=${allRecipes.size}, Usuarios=$totalUsers, Reportes=${reports.size}")

                binding.tvTotalRecipes.text = allRecipes.size.toString()
                binding.tvTotalUsers.text = totalUsers.toString()
                binding.tvTotalReports.text = reports.size.toString()
                
                // Mostrar reportes por defecto si hay, si no recetas
                binding.rvReports.layoutManager = LinearLayoutManager(this@AdminPanelActivity)
                if (reports.isNotEmpty()) {
                    binding.rvReports.adapter = ReportAdapter(reports)
                } else {
                    binding.rvReports.adapter = AdminRecipeAdapter(allRecipes)
                }
                
            } catch (e: Exception) {
                Log.e("RecetApp", "Error admin panel", e)
                Toast.makeText(this@AdminPanelActivity, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    inner class ReportAdapter(private val list: List<Reporte>) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
            LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_2, p, false)
        )
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.itemView.findViewById<android.widget.TextView>(android.R.id.text1).text = "${item.tipoObjeto.uppercase()}: ${item.motivo}"
            h.itemView.findViewById<android.widget.TextView>(android.R.id.text2).text = "ID: ${item.objetoId} - Estado: ${item.estado}"
            h.itemView.setOnClickListener {
                if (item.tipoObjeto == ResourceTypes.RECIPE) {
                    val intent = android.content.Intent(this@AdminPanelActivity, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", item.objetoId)
                    startActivity(intent)
                } else if (item.tipoObjeto == ResourceTypes.POST) {
                    val intent = android.content.Intent(this@AdminPanelActivity, PostDetailActivity::class.java)
                    intent.putExtra("POST_ID", item.objetoId)
                    startActivity(intent)
                }
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(v: android.view.View) : RecyclerView.ViewHolder(v)
    }

    inner class AdminRecipeAdapter(private val recipes: List<Receta>) : RecyclerView.Adapter<AdminRecipeAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recipe = recipes[position]
            holder.binding.tvTitle.text = recipe.nombre
            
            // Cargar imagen de la receta
            lifecycleScope.launch {
                val images = repository.getImagenesReceta(recipe.id ?: "")
                if (images.isNotEmpty()) {
                    holder.binding.ivRecipe.load(images[0]) {
                        crossfade(true)
                        placeholder(R.drawable.ic_recipe_placeholder)
                        error(R.drawable.ic_recipe_placeholder)
                    }
                } else {
                    holder.binding.ivRecipe.setImageResource(R.drawable.ic_recipe_placeholder)
                }
            }

            holder.itemView.setOnClickListener {
                val intent = android.content.Intent(this@AdminPanelActivity, RecipeDetailActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                startActivity(intent)
            }
        }
        
        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
