package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.recetapp.databinding.*
import com.google.android.material.chip.Chip
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    private val repository = SupabaseRepository(supabase)
    private var currentUser: Usuario? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkSession()
    }

    override fun onResume() {
        super.onResume()
        checkSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkSession()
    }

    private fun checkSession() {
        val session = supabase.auth.currentSessionOrNull()
        val forcedEmail = intent.getStringExtra("email_forced")
        Log.d("RecetApp", "Chequeando sesión: Auth=${session != null}, ForcedEmail=$forcedEmail")
        
        if (session != null || forcedEmail != null) {
            showFeed(forcedEmail)
        } else {
            showWelcome()
        }
    }

    private fun showWelcome() {
        setContentView(R.layout.activity_main)
        
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun showFeed(forcedEmail: String? = null) {
        val feedBinding = ViewFeedBinding.inflate(layoutInflater)
        setContentView(feedBinding.root)

        val feedAdapter = RecipeFeedAdapter(emptyList())
        feedBinding.rvFeed.apply {
            adapter = feedAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            visibility = View.VISIBLE
        }

        // Cargar recetas automáticamente
        lifecycleScope.launch {
            try {
                val recipes = repository.getAllRecetas()
                feedAdapter.updateRecipes(recipes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        feedBinding.btnGoToProfile.setOnClickListener {
            val session = supabase.auth.currentSessionOrNull()
            showProfile(session?.user?.id, forcedEmail)
        }

        feedBinding.btnGoToRecipes.setOnClickListener {
            startActivity(Intent(this, MyRecipesActivity::class.java))
        }

        feedBinding.btnLogoutFromFeed.setOnClickListener {
            signOut()
        }
    }

    private fun showProfile(userId: String? = null, forcedEmail: String? = null) {
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater)
        setContentView(profileBinding.root)

        profileBinding.tvName.text = "Buscando perfil..."
        profileBinding.tvBio.text = ""

        lifecycleScope.launch {
            try {
                // Buscamos el perfil por UID o por Email como respaldo
                val email = forcedEmail ?: supabase.auth.currentSessionOrNull()?.user?.email ?: ""
                var user = userId?.let { repository.getUsuarioByUid(it) }
                
                if (user == null && email.isNotEmpty()) {
                    user = repository.getUsuarioByEmail(email)
                }

                if (user != null) {
                    currentUser = user
                    val recipes = repository.getRecetasByAutor(user.uid ?: "")

                    profileBinding.toolbar.title = "@${user.nombreUsuario}"
                    profileBinding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
                    profileBinding.tvBio.text = user.descripcion ?: "¡Amante de la repostería en RecetApp!"
                    profileBinding.tvRecipeCount.text = user.totalRecetas.toString()
                    profileBinding.tvFollowersCount.text = user.totalSeguidores.toString()
                    profileBinding.tvFollowingCount.text = user.totalSeguidos.toString()
                    
                    profileBinding.ivVerified.visibility = View.GONE
                    profileBinding.rvRecipes.adapter = RecipeGridAdapter(recipes)
                } else {
                    Toast.makeText(this@MainActivity, "Perfil no encontrado", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("RecetApp", "Error al cargar perfil", e)
                Toast.makeText(this@MainActivity, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
            }
        }

        profileBinding.btnEditProfile.setOnClickListener { showEditProfile() }
        profileBinding.btnLogout.setOnClickListener { signOut() }
        profileBinding.toolbar.setNavigationOnClickListener { showFeed() }
    }

    private fun showEditProfile() {
        val editBinding = FragmentEditProfileBinding.inflate(layoutInflater)
        setContentView(editBinding.root)

        editBinding.toolbar.setNavigationOnClickListener { 
            val session = supabase.auth.currentSessionOrNull()
            showProfile(session?.user?.id) 
        }
        
        currentUser?.let { user ->
            editBinding.etName.setText(user.nombreCompleto)
            editBinding.etHandle.setText("@${user.nombreUsuario}")
            editBinding.etBio.setText("Amante de la repostería")
        }

        val prefs = listOf("Pastelería", "Panadería", "Sin gluten", "Vegano", "Chocolate", "Masas")
        prefs.forEach { pref ->
            val chip = Chip(this)
            chip.text = pref
            chip.isCheckable = true
            editBinding.cgPreferences.addView(chip)
        }

        editBinding.btnSave.setOnClickListener {
            currentUser?.let { user ->
                val updatedUser = user.copy(
                    nombreCompleto = editBinding.etName.text.toString(),
                    descripcion = editBinding.etBio.text.toString()
                )

                lifecycleScope.launch {
                    try {
                        repository.updateUsuario(updatedUser)
                        currentUser = updatedUser
                        Toast.makeText(this@MainActivity, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()
                        
                        val session = supabase.auth.currentSessionOrNull()
                        showProfile(session?.user?.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@MainActivity, "Error al actualizar perfil", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            try {
                supabase.auth.signOut()
                // Limpiamos los datos del Intent para que no se re-loguee automáticamente
                intent.removeExtra("email_forced")
                currentUser = null
                checkSession()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Adapters ---

    inner class RecipeGridAdapter(private val recipes: List<Receta>) : RecyclerView.Adapter<RecipeGridAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recipe = recipes[position]
            holder.binding.tvTitle.text = recipe.nombre
            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class RecipeFeedAdapter(private var recipes: List<Receta>) : RecyclerView.Adapter<RecipeFeedAdapter.ViewHolder>() {
        fun updateRecipes(newRecipes: List<Receta>) {
            recipes = newRecipes
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recipe = recipes[position]
            holder.binding.tvTitle.text = recipe.nombre
            holder.binding.tvDescription.text = recipe.descripcion ?: "Sin descripción"
            holder.binding.tvDifficulty.text = "${recipe.dificultad?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Media"} 🍰"
            
            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeFeedBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
