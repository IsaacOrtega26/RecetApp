package com.example.recetapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.*
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private var currentStep = 0
    private val steps = listOf("Información", "Ingredientes", "Pasos", "Imagen y visibilidad")

    // Recipe Data
    private var recipeName = ""
    private var category = "Dulce"
    private var difficulty = "Media"
    private var totalTime = ""
    private val ingredients = mutableListOf(Ingredient())
    private val recipeSteps = mutableListOf(RecipeStep())
    private var visibility = Visibility.PUBLICA

    // Sample Data for Profile
    private val me = User(
        name = "Isaac",
        handle = "isaac_dev",
        bio = "Apasionado de la cocina y el código. Especialista en postres sin gluten.",
        avatar = "",
        verified = true,
        recipes = 12,
        followers = 1240,
        following = 85
    )

    private val sampleRecipes = listOf(
        Recipe("1", "Tarta de Queso", ""),
        Recipe("2", "Cookies de Avena", ""),
        Recipe("3", "Brownie Vegano", ""),
        Recipe("4", "Pan de Masa Madre", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // For this demo, let's start directly on the Profile View
        showProfile()
    }

    private fun showProfile() {
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater)
        setContentView(profileBinding.root)

        profileBinding.toolbar.title = "@${me.handle}"
        profileBinding.tvName.text = me.name
        profileBinding.tvBio.text = me.bio
        profileBinding.tvRecipeCount.text = me.recipes.toString()
        profileBinding.tvFollowersCount.text = me.followers.toString()
        profileBinding.tvFollowingCount.text = me.following.toString()
        profileBinding.ivVerified.visibility = if (me.verified) View.VISIBLE else View.GONE

        profileBinding.rvRecipes.adapter = RecipeGridAdapter(sampleRecipes)

        profileBinding.btnEditProfile.setOnClickListener {
            showEditProfile()
        }
    }

    private fun showEditProfile() {
        val editBinding = FragmentEditProfileBinding.inflate(layoutInflater)
        setContentView(editBinding.root)

        editBinding.toolbar.setNavigationOnClickListener { showProfile() }
        editBinding.etName.setText(me.name)
        editBinding.etHandle.setText("@${me.handle}")
        editBinding.etBio.setText(me.bio)

        val prefs = listOf("Pastelería", "Panadería", "Sin gluten", "Vegano", "Chocolate", "Masas")
        prefs.forEach { pref ->
            val chip = Chip(this)
            chip.text = pref
            chip.isCheckable = true
            editBinding.cgPreferences.addView(chip)
        }

        editBinding.btnSave.setOnClickListener {
            // Save logic
            showProfile()
        }
    }

    // --- Adapters ---

    inner class RecipeGridAdapter(private val recipes: List<Recipe>) : RecyclerView.Adapter<RecipeGridAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recipe = recipes[position]
            holder.binding.tvTitle.text = recipe.title
            // Image loading with Coil/Glide would go here
        }

        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }

    // --- Create Recipe Wizard Logic (Previous code kept for reference or used if needed) ---
    // ...
}
