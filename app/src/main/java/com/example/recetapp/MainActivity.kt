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
import kotlinx.coroutines.launch
import coil.load
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    private val repository = SupabaseRepository(supabase)
    private var currentUser: Usuario? = null

    private lateinit var mainBinding: ActivityMainBinding

    private val pickProfileImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { updateProfilePreview(it) }
    }

    private var profileImageUri: android.net.Uri? = null
    private fun updateProfilePreview(uri: android.net.Uri) {
        profileImageUri = uri
        mainBinding.contentFrame.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivEditAvatar)?.setImageURI(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        checkSession()
    }

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }

    private fun checkSession() {
        val session = supabase.auth.currentSessionOrNull()
        if (session != null) {
            setupMainShell()
        } else {
            showWelcome()
        }
    }

    private fun showWelcome() {
        setContentView(R.layout.activity_welcome)
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        findViewById<Button>(R.id.btnLogin).setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
    }

    private fun setupMainShell() {
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        val navListener = com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_feed -> { showFeed(); true }
                R.id.nav_explore -> { showExplore(); true }
                R.id.nav_notifications -> { showNotifications(); true }
                R.id.nav_profile -> { showProfile(supabase.auth.currentSessionOrNull()?.user?.id); true }
                else -> false
            }
        }

        try {
            mainBinding.bottomNavigation?.setOnItemSelectedListener(navListener)
            mainBinding.fabCreate?.setOnClickListener {
                startActivity(Intent(this, CreateRecipeActivity::class.java))
            }
        } catch (e: Exception) {
            Log.e("RecetApp", "Error binding navigation", e)
        }

        showFeed()
    }

    private fun showFeed() {
        mainBinding.contentFrame.removeAllViews()
        val feedBinding = ViewFeedBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        val feedAdapter = RecipeFeedAdapter(emptyList())
        feedBinding.rvFeed.apply {
            adapter = feedAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
        }

        val refreshFeed = {
            lifecycleScope.launch {
                try {
                    val recipes = repository.getAllRecetas().filter { it.visibilidad == "publica" }
                    feedAdapter.updateRecipes(recipes)
                    feedBinding.swipeRefresh.isRefreshing = false
                } catch (e: Exception) {
                    feedBinding.swipeRefresh.isRefreshing = false
                }
            }
        }

        feedBinding.swipeRefresh.setOnRefreshListener { refreshFeed() }
        refreshFeed()
    }

    private fun showExplore() {
        mainBinding.contentFrame.removeAllViews()
        ViewExploreBinding.inflate(layoutInflater, mainBinding.contentFrame, true)
    }

    private fun showNotifications() {
        mainBinding.contentFrame.removeAllViews()
        ViewNotificationsBinding.inflate(layoutInflater, mainBinding.contentFrame, true)
    }

    private fun showProfile(userId: String? = null) {
        mainBinding.contentFrame.removeAllViews()
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        lifecycleScope.launch {
            try {
                // IMPORTANT: Use UID for lookup
                val user = userId?.let { repository.getUsuarioByUid(it) } ?: 
                           supabase.auth.currentSessionOrNull()?.user?.id?.let { repository.getUsuarioByUid(it) }

                if (user != null) {
                    currentUser = user
                    val recipes = repository.getRecetasByAutor(user.uid ?: "")

                    profileBinding.toolbar.title = "@${user.nombreUsuario}"
                    profileBinding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
                    profileBinding.tvBio.text = user.descripcion ?: "¡Amante de la repostería!"
                    profileBinding.tvRecipeCount.text = user.totalRecetas.toString()
                    profileBinding.tvFollowersCount.text = user.totalSeguidores.toString()
                    profileBinding.tvFollowingCount.text = user.totalSeguidos.toString()
                    
                    user.fotoUrl?.let { profileBinding.ivAvatar.load(it) }
                    profileBinding.rvRecipes.adapter = RecipeGridAdapter(recipes)
                    
                    // ADMIN PANEL VISIBILITY
                    if (user.rol == "administrador") {
                        profileBinding.btnAdminPanel.visibility = View.VISIBLE
                        profileBinding.btnAdminPanel.setOnClickListener {
                            Toast.makeText(this@MainActivity, "Abriendo panel de administración...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RecetApp", "Error al cargar perfil", e)
            }
        }

        profileBinding.btnEditProfile.setOnClickListener { showEditProfile() }
        profileBinding.btnLogout.setOnClickListener { signOut() }
        profileBinding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun showEditProfile() {
        mainBinding.contentFrame.removeAllViews()
        val editBinding = FragmentEditProfileBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        editBinding.toolbar.setNavigationOnClickListener { showProfile(supabase.auth.currentSessionOrNull()?.user?.id) }
        
        currentUser?.let { user ->
            editBinding.etName.setText(user.nombreCompleto)
            editBinding.etHandle.setText("@${user.nombreUsuario}")
            editBinding.etBio.setText(user.descripcion)
            user.fotoUrl?.let { editBinding.ivEditAvatar.load(it) }
        }

        editBinding.btnChangePhoto.setOnClickListener { pickProfileImage.launch("image/*") }
        
        editBinding.btnSave.setOnClickListener {
            currentUser?.let { user ->
                lifecycleScope.launch {
                    try {
                        var finalFotoUrl = user.fotoUrl
                        profileImageUri?.let { uri ->
                            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null) {
                                val fileName = "avatar_${user.uid}.jpg"
                                val uploadedUrl = repository.uploadImage("avatars", fileName, bytes)
                                if (uploadedUrl != null) finalFotoUrl = uploadedUrl
                            }
                        }

                        val updatedUser = user.copy(
                            nombreCompleto = editBinding.etName.text.toString(),
                            descripcion = editBinding.etBio.text.toString(),
                            fotoUrl = finalFotoUrl
                        )

                        repository.updateUsuario(updatedUser)
                        currentUser = updatedUser
                        showProfile(user.uid)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            try {
                supabase.auth.signOut()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

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
            holder.binding.tvLikesCount.text = recipe.totalLikes.toString()
            holder.binding.tvCommentsCount.text = recipe.totalComentarios.toString()

            lifecycleScope.launch {
                val images = repository.getImagenesReceta(recipe.id ?: "")
                if (images.isNotEmpty()) {
                    holder.binding.ivRecipe.load(images[0])
                } else {
                    holder.binding.ivRecipe.setImageResource(R.drawable.ic_recipe_placeholder)
                }
            }

            // RED SOCIAL - LIKES
            holder.binding.ivLike.setOnClickListener {
                val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
                lifecycleScope.launch {
                    try {
                        repository.toggleLike(currentUid, recipe.id ?: "", "receta")
                        val updated = repository.getRecetaById(recipe.id ?: "")
                        updated?.let {
                            holder.binding.tvLikesCount.text = it.totalLikes.toString()
                        }
                    } catch (e: Exception) {}
                }
            }

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
