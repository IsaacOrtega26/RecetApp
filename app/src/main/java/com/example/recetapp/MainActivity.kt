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
            currentUser = null // Limpiar rastro de sesión anterior
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
                    val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: ""
                    val isFollowingTab = feedBinding.tabLayoutFeed.selectedTabPosition == 1
                    
                    val allRecipes = repository.getAllRecetas().filter { it.visibilidad == "publica" }
                    
                    val filtered = if (isFollowingTab) {
                        // Filtrar solo los que sigue
                        allRecipes.filter { repository.isFollowing(currentUid, it.autorUid) }
                    } else {
                        allRecipes
                    }
                    
                    feedAdapter.updateRecipes(filtered)
                    feedBinding.swipeRefresh.isRefreshing = false
                } catch (e: Exception) {
                    feedBinding.swipeRefresh.isRefreshing = false
                }
            }
        }

        feedBinding.tabLayoutFeed.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { refreshFeed() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        feedBinding.swipeRefresh.setOnRefreshListener { refreshFeed() }
        refreshFeed()
    }

    private fun showExplore() {
        mainBinding.contentFrame.removeAllViews()
        val exploreBinding = ViewExploreBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        val feedAdapter = RecipeFeedAdapter(emptyList())
        exploreBinding.rvExplore.apply {
            adapter = feedAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
        }

        exploreBinding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                lifecycleScope.launch {
                    val all = repository.getAllRecetas()
                    val filtered = all.filter { 
                        it.nombre.lowercase().contains(query) || 
                        it.descripcion?.lowercase()?.contains(query) == true 
                    }
                    feedAdapter.updateRecipes(filtered)
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        lifecycleScope.launch {
            val recipes = repository.getAllRecetas()
            feedAdapter.updateRecipes(recipes)
        }
    }

    private fun showNotifications() {
        mainBinding.contentFrame.removeAllViews()
        val notifBinding = ViewNotificationsBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        lifecycleScope.launch {
            val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            val list = repository.getNotificaciones(currentUid)
            notifBinding.rvNotifications.adapter = NotificationAdapter(list)
        }
    }

    inner class NotificationAdapter(private val list: List<Notificacion>) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemCommentBinding.inflate(layoutInflater, p, false)) // Reusamos diseño similar
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val n = list[pos]
            val text = when(n.tipo) {
                "like" -> "le dio me gusta a tu receta"
                "seguidor" -> "empezó a seguirte"
                "comentario" -> "comentó tu receta"
                else -> "interactuó contigo"
            }
            h.binding.tvContent.text = text
            lifecycleScope.launch {
                val author = repository.getUsuarioByUid(n.actorUid)
                h.binding.tvAuthor.text = author?.nombreUsuario ?: "Alguien"
                author?.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showComments(recetaId: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val b = DialogCommentsBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        val loadComments = {
            lifecycleScope.launch {
                val comments = repository.getComentarios(recetaId)
                b.rvComments.adapter = CommentAdapter(comments)
            }
        }

        b.btnSendComment.setOnClickListener {
            val content = b.etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
                lifecycleScope.launch {
                    val success = repository.insertComentario(Comentario(
                        autorUid = uid,
                        recursoId = recetaId,
                        tipoRecurso = "receta",
                        contenido = content
                    ))
                    if (success) {
                        b.etComment.setText("")
                        loadComments()
                    }
                }
            }
        }

        loadComments()
        dialog.show()
    }

    inner class CommentAdapter(private val list: List<Comentario>) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemCommentBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.binding.tvContent.text = item.contenido
            lifecycleScope.launch {
                val author = repository.getUsuarioByUid(item.autorUid)
                h.binding.tvAuthor.text = author?.nombreUsuario ?: "Anónimo"
                author?.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showProfile(userId: String? = null) {
        mainBinding.contentFrame.removeAllViews()
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        lifecycleScope.launch {
            try {
                // IMPORTANT: Use UID for lookup
                val sessionUser = supabase.auth.currentSessionOrNull()?.user
                val currentId = userId ?: sessionUser?.id
                Log.d("RecetApp", "Cargando perfil para UID: $currentId")
                
                var user = currentId?.let { repository.getUsuarioByUid(it) }
                
                // FALLBACK: Si no lo encuentra por UID, intentamos por Email (por si hubo conflicto de sincronización)
                if (user == null && sessionUser?.email != null) {
                    Log.d("RecetApp", "UID no encontrado, intentando fallback por email: ${sessionUser.email}")
                    user = repository.getUsuarioByEmail(sessionUser.email!!)
                }

                if (user != null) {
                    Log.d("RecetApp", "Usuario cargado: ${user.nombreUsuario}, Foto: ${user.fotoUrl}")
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
                    Log.d("RecetApp", "Rol del usuario: ${user.rol}")
                    if (user.rol.lowercase() == "administrador" || user.rol.lowercase() == "admin") {
                        profileBinding.btnAdminPanel.visibility = View.VISIBLE
                        profileBinding.btnAdminPanel.setOnClickListener {
                            val intent = Intent(this@MainActivity, AdminPanelActivity::class.java)
                            startActivity(intent)
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
                currentUser = null
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
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
                Log.d("RecetApp", "Feed - Receta: ${recipe.nombre}, Imágenes encontradas: ${images.size}")
                if (images.isNotEmpty()) {
                    Log.d("RecetApp", "Feed - Cargando imagen: ${images[0]}")
                    holder.binding.ivRecipe.load(images[0]) {
                        crossfade(true)
                        placeholder(R.drawable.ic_recipe_placeholder)
                        error(R.drawable.ic_recipe_placeholder)
                    }
                } else {
                    holder.binding.ivRecipe.setImageResource(R.drawable.ic_recipe_placeholder)
                }
            }

            // RED SOCIAL - LIKES (CON ACTUALIZACIÓN OPTIMISTA Y VERIFICACIÓN)
            holder.binding.ivLike.setOnClickListener {
                val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
                lifecycleScope.launch {
                    try {
                        // 1. Efecto visual inmediato (Optimista)
                        // IMPORTANTE: Basamos la decisión en el color actual del icono
                        val isCurrentlyLiked = holder.binding.ivLike.imageTintList == android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
                        val currentLikes = holder.binding.tvLikesCount.text.toString().toIntOrNull() ?: 0
                        
                        if (!isCurrentlyLiked) {
                            holder.binding.tvLikesCount.text = (currentLikes + 1).toString()
                            holder.binding.ivLike.setImageResource(R.drawable.ic_heart)
                            holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
                        } else {
                            holder.binding.tvLikesCount.text = (if (currentLikes > 0) currentLikes - 1 else 0).toString()
                            holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
                        }

                        // 2. Operación real en BD
                        Log.d("RecetApp", "Interacción social enviada (Like=$isCurrentlyLiked)...")
                        repository.toggleLike(currentUid, recipe.id ?: "", "receta")
                        
                        // 3. Confirmación final (Sincronización real)
                        // Consultamos la receta de nuevo para obtener el número exacto del servidor
                        val updated = repository.getRecetaById(recipe.id ?: "")
                        updated?.let {
                            holder.binding.tvLikesCount.text = it.totalLikes.toString()
                            Log.d("RecetApp", "Likes actualizados desde servidor: ${it.totalLikes}")
                        }
                    } catch (e: Exception) {
                        Log.e("RecetApp", "Fallo al procesar Like: ${e.message}")
                        Toast.makeText(this@MainActivity, "Error de sincronización", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            holder.binding.ivComment.setOnClickListener {
                showComments(recipe.id ?: "")
            }

            holder.binding.ivShare.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Mira esta receta en RecetApp")
                    putExtra(Intent.EXTRA_TEXT, "¡Mira la receta de ${recipe.nombre} en RecetApp! \n\n${recipe.descripcion}")
                }
                startActivity(Intent.createChooser(shareIntent, "Compartir receta"))
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
