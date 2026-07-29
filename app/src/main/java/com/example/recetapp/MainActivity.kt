package com.example.recetapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.*
import com.google.android.material.chip.Chip
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    private val repository = SupabaseRepository(supabase)
    private var currentUser: Usuario? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show Profile by default
        showProfile()
    }

    private fun showProfile() {
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater)
        setContentView(profileBinding.root)

        // Load data from Supabase
        lifecycleScope.launch {
            try {
                // 1. Get user data
                val user = repository.getUsuarioByHandle("isaac_dev")
                if (user != null) {
                    currentUser = user

                    // 2. Get user recipes
                    val recipes = repository.getRecetasByAutor(user.uid ?: "")

                    // 3. Update UI
                    profileBinding.toolbar.title = "@${user.nombreUsuario}"
                    profileBinding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
                    profileBinding.tvBio.text = user.descripcion ?: "Sin descripción"
                    profileBinding.tvRecipeCount.text = user.totalRecetas.toString()
                    profileBinding.tvFollowersCount.text = user.totalSeguidores.toString()
                    profileBinding.tvFollowingCount.text = user.totalSeguidos.toString()
                    
                    // Note: 'verified' not in SQL, using placeholder
                    profileBinding.ivVerified.visibility = View.GONE

                    profileBinding.rvRecipes.adapter = RecipeGridAdapter(recipes)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        profileBinding.btnEditProfile.setOnClickListener {
            showEditProfile()
        }
    }

    private fun showEditProfile() {
        val editBinding = FragmentEditProfileBinding.inflate(layoutInflater)
        setContentView(editBinding.root)

        editBinding.toolbar.setNavigationOnClickListener { showProfile() }
        
        currentUser?.let { user ->
            editBinding.etName.setText(user.nombreCompleto)
            editBinding.etHandle.setText("@${user.nombreUsuario}")
            editBinding.etBio.setText(user.descripcion)
        }

        val prefs = listOf("Pastelería", "Panadería", "Sin gluten", "Vegano", "Chocolate", "Masas")
        prefs.forEach { pref ->
            val chip = Chip(this)
            chip.text = pref
            chip.isCheckable = true
            editBinding.cgPreferences.addView(chip)
        }

        editBinding.btnSave.setOnClickListener {
            // Here you would implement update logic to Supabase using repository.updateUsuario
            showProfile()
        }
    }

    // --- Recipe Grid Adapter ---

    inner class RecipeGridAdapter(private val recipes: List<Receta>) : RecyclerView.Adapter<RecipeGridAdapter.ViewHolder>() {
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
