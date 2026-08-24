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
import com.example.recetapp.databinding.ItemReportAdminBinding
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
        refreshData()
    }

    private fun refreshData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
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
                    // Si no hay reportes pendientes, mostrar mensaje o lista de recetas
                    binding.rvReports.adapter = AdminRecipeAdapter(allRecipes)
                    if (allRecipes.isEmpty()) {
                        Toast.makeText(this@AdminPanelActivity, "No hay contenido para mostrar", Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("RecetApp", "Error admin panel", e)
                Toast.makeText(this@AdminPanelActivity, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    inner class ReportAdapter(private val list: List<Reporte>) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(
            ItemReportAdminBinding.inflate(LayoutInflater.from(p.context), p, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
            val item = list[pos]
            holder.binding.tvType.text = "${item.tipoObjeto} (${item.estado})"
            holder.binding.tvReason.text = item.motivo
            holder.binding.tvTargetId.text = "ID: ${item.objetoId}"

            // Solo mostrar botones si está pendiente
            val isPending = item.estado == "pendiente"
            holder.binding.btnDismiss.isEnabled = isPending
            holder.binding.btnDelete.isEnabled = isPending
            holder.binding.btnDismiss.alpha = if (isPending) 1.0f else 0.5f
            holder.binding.btnDelete.alpha = if (isPending) 1.0f else 0.5f

            holder.binding.btnDismiss.setOnClickListener {
                lifecycleScope.launch {
                    val success = repository.resolveReporte(item.id ?: "", false)
                    if (success) {
                        Toast.makeText(this@AdminPanelActivity, "Reporte descartado", Toast.LENGTH_SHORT).show()
                        refreshData()
                    } else {
                        Toast.makeText(this@AdminPanelActivity, "Error al actualizar reporte. Verifique permisos RLS.", Toast.LENGTH_LONG).show()
                    }
                }
            }

            holder.binding.btnView.setOnClickListener {
                if (item.tipoObjeto == ResourceTypes.RECIPE) {
                    val intent = android.content.Intent(this@AdminPanelActivity, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", item.objetoId)
                    startActivity(intent)
                } else if (item.tipoObjeto == ResourceTypes.POST) {
                    val intent = android.content.Intent(this@AdminPanelActivity, PostDetailActivity::class.java)
                    intent.putExtra("POST_ID", item.objetoId)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@AdminPanelActivity, "Vista no disponible para este tipo", Toast.LENGTH_SHORT).show()
                }
            }

            holder.binding.btnDelete.setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@AdminPanelActivity)
                    .setTitle("Eliminar contenido")
                    .setMessage("¿Estás seguro de que deseas eliminar este contenido permanentemente?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        lifecycleScope.launch {
                            val success = repository.resolveReporte(item.id ?: "", true)
                            if (success) {
                                Toast.makeText(this@AdminPanelActivity, "Contenido eliminado y reporte revisado", Toast.LENGTH_SHORT).show()
                                refreshData()
                            } else {
                                Toast.makeText(this@AdminPanelActivity, "Error al procesar eliminación. Verifique permisos RLS.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemReportAdminBinding) : RecyclerView.ViewHolder(binding.root)
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
