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
                // Estadísticas
                val allRecipes = repository.getAllRecetas()
                val totalUsers = repository.getTotalUsersCount()
                val reports = repository.getReportes()
                
                binding.tvTotalRecipes.text = allRecipes.size.coerceAtLeast(0).toString()
                binding.tvTotalUsers.text = totalUsers.coerceAtLeast(0).toString()
                binding.tvTotalReports.text = reports.size.coerceAtLeast(0).toString()
                
                // Lista de Reportes (o recetas para moderación)
                binding.rvReports.layoutManager = LinearLayoutManager(this@AdminPanelActivity)
                binding.rvReports.adapter = AdminRecipeAdapter(allRecipes)
                
            } catch (e: Exception) {
                Log.e("RecetApp", "Error admin panel", e)
                Toast.makeText(this@AdminPanelActivity, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class AdminRecipeAdapter(private val recipes: List<Receta>) : RecyclerView.Adapter<AdminRecipeAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recipe = recipes[position]
            holder.binding.tvTitle.text = recipe.nombre
        }
        
        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
