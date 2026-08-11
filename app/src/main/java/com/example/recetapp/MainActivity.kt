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
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    private val repository = SupabaseRepository(supabase)
    private var currentUser: Usuario? = null

    private lateinit var mainBinding: ActivityMainBinding
    private var currentViewState = "FEED" // "FEED", "EXPLORE", "NOTIF", "PROFILE", "OTHER_PROFILE", "EDIT_PROFILE", "CHATS"

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
        checkSession()
        setupBackNavigation()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentViewState) {
                    "OTHER_PROFILE" -> showExplore()
                    "CHATS" -> showFeed()
                    "EDIT_PROFILE" -> showProfile(supabase.auth.currentSessionOrNull()?.user?.id)
                    "FEED" -> finish()
                    else -> showFeed()
                }
            }
        })
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
                // Selector entre Receta y Publicación
                val options = arrayOf("Nueva Receta", "Nueva Publicación")
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("¿Qué quieres crear?")
                    .setItems(options) { _, which ->
                        if (which == 0) {
                            startActivity(Intent(this, CreateRecipeActivity::class.java))
                        } else {
                            startActivity(Intent(this, CreatePostActivity::class.java))
                        }
                    }
                    .show()
            }
        } catch (e: Exception) {
            Log.e("RecetApp", "Error binding navigation", e)
        }

        showFeed()
    }

    private fun showFeed() {
        currentViewState = "FEED"
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
                    val allPosts = repository.getFeedPublicaciones().filter { it.visibilidad == "publica" }
                    
                    // Combinamos ambos contenidos
                    val combined: List<Any> = (allRecipes + allPosts).sortedByDescending { 
                        if (it is Receta) it.fechaCreacion else (it as Publicacion).fechaCreacion 
                    }
                    
                    val filtered = if (isFollowingTab) {
                        combined.filter { 
                            val autorId = if (it is Receta) it.autorUid else (it as Publicacion).autorUid
                            repository.isFollowing(currentUid, autorId) 
                        }
                    } else {
                        combined
                    }
                    
                    feedAdapter.updateItems(filtered)
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

        feedBinding.ivChat.setOnClickListener { showChats() }
    }

    private fun showChats() {
        startActivity(Intent(this, MessagesActivity::class.java))
    }

    private fun showExplore() {
        currentViewState = "EXPLORE"
        mainBinding.contentFrame.removeAllViews()
        val exploreBinding = ViewExploreBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        exploreBinding.btnBackFromExplore?.setOnClickListener { showFeed() }

        val recipeAdapter = RecipeFeedAdapter(emptyList())
        val userAdapter = UserSearchAdapter(emptyList())

        exploreBinding.rvExplore.apply {
            adapter = recipeAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
        }

        exploreBinding.tabExplore.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                if (tab?.position == 0) {
                    exploreBinding.rvExplore.adapter = recipeAdapter
                    exploreBinding.etSearch.hint = "Buscar recetas..."
                } else {
                    exploreBinding.rvExplore.adapter = userAdapter
                    exploreBinding.etSearch.hint = "Buscar personas..."
                }
                triggerSearch(exploreBinding.etSearch.text.toString())
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}

            private fun triggerSearch(query: String) {
                lifecycleScope.launch {
                    if (exploreBinding.tabExplore.selectedTabPosition == 0) {
                        val results = repository.searchRecetas(query)
                        recipeAdapter.updateRecipes(results)
                    } else {
                        val results = repository.searchUsuarios(query)
                        userAdapter.updateUsers(results)
                    }
                }
            }
        })

        exploreBinding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                lifecycleScope.launch {
                    if (exploreBinding.tabExplore.selectedTabPosition == 0) {
                        val results = repository.searchRecetas(query)
                        recipeAdapter.updateRecipes(results)
                    } else {
                        val results = repository.searchUsuarios(query)
                        userAdapter.updateUsers(results)
                    }
                }
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        lifecycleScope.launch {
            val recipes = repository.getAllRecetas()
            recipeAdapter.updateRecipes(recipes)
        }
    }

    inner class UserSearchAdapter(private var users: List<Usuario>) : RecyclerView.Adapter<UserSearchAdapter.ViewHolder>() {
        fun updateUsers(newUsers: List<Usuario>) {
            users = newUsers
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemUserSearchBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val user = users[pos]
            h.binding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
            h.binding.tvHandle.text = "@${user.nombreUsuario}"
            user.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            h.itemView.setOnClickListener { showProfile(user.uid) }
        }
        override fun getItemCount() = users.size
        inner class ViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showNotifications() {
        currentViewState = "NOTIF"
        mainBinding.contentFrame.removeAllViews()
        val notifBinding = ViewNotificationsBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        notifBinding.btnBackFromNotif.setOnClickListener { showFeed() }

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
                "solicitud" -> "quiere seguirte"
                else -> "interactuó contigo"
            }
            h.binding.tvContent.text = text
            h.itemView.setOnClickListener {
                if (n.tipo == "mensaje") {
                    val intent = Intent(this@MainActivity, ChatActivity::class.java)
                    intent.putExtra("OTHER_USER_ID", n.actorUid)
                    startActivity(intent)
                }
            }
            lifecycleScope.launch {
                val author = repository.getUsuarioByUid(n.actorUid)
                h.binding.tvAuthor.text = author?.nombreUsuario ?: "Alguien"
                author?.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showComments(recetaId: String, tipo: String = "receta") {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val b = DialogCommentsBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        b.btnCloseComments.setOnClickListener { dialog.dismiss() }

        fun loadComments() {
            lifecycleScope.launch {
                val comments = repository.getComentarios(recetaId)
                b.rvComments.adapter = CommentAdapter(comments) { loadComments() }
            }
        }

        b.btnSendComment.setOnClickListener {
            val content = b.etComment.text.toString().trim()
            if (content.isNotEmpty()) {
                val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
                b.btnSendComment.isEnabled = false
                lifecycleScope.launch {
                    try {
                        val success = repository.insertComentario(Comentario(
                            autorUid = uid,
                            recursoId = recetaId,
                            tipoRecurso = tipo,
                            contenido = content
                        ))
                        if (success) {
                            b.etComment.setText("")
                            loadComments()
                            Toast.makeText(this@MainActivity, "Comentario enviado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Error al enviar. Intenta de nuevo.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Fallo de conexión", Toast.LENGTH_SHORT).show()
                    } finally {
                        b.btnSendComment.isEnabled = true
                    }
                }
            }
        }

        loadComments()
        dialog.show()
    }

    inner class CommentAdapter(
        private val list: List<Comentario>,
        private val onCommentDeleted: () -> Unit
    ) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemCommentBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.binding.tvContent.text = item.contenido

            val currentUid = supabase.auth.currentSessionOrNull()?.user?.id
            if (item.autorUid == currentUid) {
                h.binding.btnDeleteComment.visibility = View.VISIBLE
                h.binding.btnDeleteComment.setOnClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eliminar comentario")
                        .setMessage("¿Deseas eliminar este comentario?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            val comentarioId = item.id
                            if (comentarioId != null && currentUid != null) {
                                lifecycleScope.launch {
                                    val success = repository.deleteComentario(comentarioId, currentUid)
                                    if (success) {
                                        onCommentDeleted()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Error al eliminar comentario", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            } else {
                h.binding.btnDeleteComment.visibility = View.GONE
                h.binding.btnDeleteComment.setOnClickListener(null)
            }

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
        val sessionUser = supabase.auth.currentSessionOrNull()?.user
        val isOwnProfile = userId == null || userId == sessionUser?.id
        currentViewState = if (isOwnProfile) "PROFILE" else "OTHER_PROFILE"

        mainBinding.contentFrame.removeAllViews()
        val profileBinding = FragmentProfileBinding.inflate(layoutInflater, mainBinding.contentFrame, true)

        profileBinding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back)
        profileBinding.toolbar.setNavigationIconTint(getColor(R.color.text_primary))
        profileBinding.toolbar.setNavigationOnClickListener { 
            if (isOwnProfile) showFeed() else showExplore() 
        }
        
        if (isOwnProfile) {
            profileBinding.btnSettings.visibility = View.VISIBLE
            profileBinding.btnLogout.visibility = View.VISIBLE
            profileBinding.btnEditProfile.visibility = View.VISIBLE
            profileBinding.btnFollowProfile.visibility = View.GONE
            profileBinding.btnSendMessage.visibility = View.GONE
            profileBinding.btnPrivacy.visibility = View.VISIBLE
        } else {
            profileBinding.btnSettings.visibility = View.GONE
            profileBinding.btnLogout.visibility = View.GONE
            profileBinding.btnEditProfile.visibility = View.GONE
            profileBinding.btnFollowProfile.visibility = View.VISIBLE
            profileBinding.btnSendMessage.visibility = View.VISIBLE
            profileBinding.btnPrivacy.visibility = View.GONE
        }

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

                    // SOLICITUDES DE SEGUIMIENTO: solo visible en el perfil propio
                    if (user.uid == sessionUser?.id) {
                        profileBinding.btnFollowRequests.visibility = View.VISIBLE
                        profileBinding.btnFollowRequests.setOnClickListener {
                            showFollowRequests(user.uid ?: "")
                        }
                    }

                    profileBinding.btnSendMessage.setOnClickListener {
                        val intent = Intent(this@MainActivity, ChatActivity::class.java)
                        intent.putExtra("OTHER_USER_ID", user.uid)
                        startActivity(intent)
                    }

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

    private fun showFollowRequests(uid: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val b = DialogFollowRequestsBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        b.btnClose.setOnClickListener { dialog.dismiss() }

        fun loadRequests() {
            lifecycleScope.launch {
                val requests = repository.getSolicitudesPendientes(uid)
                b.tvEmptyRequests.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
                b.rvFollowRequests.adapter = FollowRequestAdapter(requests) { loadRequests() }
            }
        }

        loadRequests()
        dialog.show()
    }

    inner class FollowRequestAdapter(
        private val list: List<SolicitudSeguimiento>,
        private val onHandled: () -> Unit
    ) : RecyclerView.Adapter<FollowRequestAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemFollowRequestBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            lifecycleScope.launch {
                val solicitante = repository.getUsuarioByUid(item.solicitanteUid)
                h.binding.tvName.text = solicitante?.nombreCompleto ?: solicitante?.nombreUsuario ?: "Usuario"
                h.binding.tvHandle.text = "@${solicitante?.nombreUsuario ?: ""}"
                solicitante?.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            }
            h.binding.btnAccept.setOnClickListener {
                val solicitudId = item.id ?: return@setOnClickListener
                lifecycleScope.launch {
                    val success = repository.aceptarSolicitud(solicitudId, item.solicitanteUid, item.destinoUid)
                    if (success) onHandled()
                    else Toast.makeText(this@MainActivity, "Error al aceptar la solicitud", Toast.LENGTH_SHORT).show()
                }
            }
            h.binding.btnReject.setOnClickListener {
                val solicitudId = item.id ?: return@setOnClickListener
                lifecycleScope.launch {
                    val success = repository.rechazarSolicitud(solicitudId)
                    if (success) onHandled()
                    else Toast.makeText(this@MainActivity, "Error al rechazar la solicitud", Toast.LENGTH_SHORT).show()
                }
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemFollowRequestBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun showEditProfile() {
        currentViewState = "EDIT_PROFILE"
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

    inner class RecipeFeedAdapter(private var items: List<Any>) : RecyclerView.Adapter<RecipeFeedAdapter.ViewHolder>() {
        
        private var likedIds = mutableSetOf<String>()

        fun updateItems(newItems: List<Any>) {
            items = newItems
            fetchLikes()
            notifyDataSetChanged()
        }
        
        private fun fetchLikes() {
            val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
            lifecycleScope.launch {
                try {
                    val ids = repository.getLikedResourceIds(uid)
                    likedIds.clear()
                    likedIds.addAll(ids)
                    notifyDataSetChanged() // Refrescar corazones
                } catch (e: Exception) { }
            }
        }

        fun updateRecipes(newRecipes: List<Receta>) {
            items = newRecipes
            fetchLikes()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
            ViewHolder(ItemRecipeFeedBinding.inflate(layoutInflater, parent, false))
            
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isRecipe = item is Receta
            
            val id: String?
            val autorUid: String
            val nombre: String
            val descripcion: String?
            val totalLikes: Int
            val totalComentarios: Int
            val categoria: String?

            if (isRecipe) {
                val r = item as Receta
                id = r.id
                autorUid = r.autorUid
                nombre = r.nombre
                descripcion = r.descripcion
                totalLikes = r.totalLikes
                totalComentarios = r.totalComentarios
                categoria = r.categoria
                holder.binding.tvDifficulty.visibility = View.VISIBLE
                holder.binding.tvDifficulty.text = "${r.dificultad?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Media"} 🍰"
            } else {
                val p = item as Publicacion
                id = p.id
                autorUid = p.autorUid
                nombre = "Publicación"
                descripcion = p.descripcion
                totalLikes = p.totalLikes
                totalComentarios = p.totalComentarios
                categoria = null
                holder.binding.tvDifficulty.visibility = View.GONE
            }

            holder.binding.tvTitle.text = nombre
            holder.binding.tvDescription.text = descripcion ?: "Sin descripción"
            holder.binding.tvLikesCount.text = totalLikes.toString()
            holder.binding.tvCommentsCount.text = totalComentarios.toString()

            // Cargar estado real del Like
            val isAlreadyLiked = likedIds.contains(id ?: "")
            if (isAlreadyLiked) {
                holder.binding.ivLike.tag = "liked"
                holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
            } else {
                holder.binding.ivLike.tag = "unliked"
                holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
            }

            lifecycleScope.launch {
                val images = if (isRecipe) repository.getImagenesReceta(id ?: "") else repository.getImagenesPublicacion(id ?: "")
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

            // RED SOCIAL - LIKES
            holder.binding.ivLike.setOnClickListener {
                val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@setOnClickListener
                
                val currentLikes = holder.binding.tvLikesCount.text.toString().toIntOrNull() ?: 0
                val isLiked = holder.binding.ivLike.tag == "liked"
                
                // Si el usuario quiere "dar like solo 1 vez", podemos hacer que si ya dio like,
                // no haga nada o unliké. Para evitar múltiples likes accidentales:
                holder.binding.ivLike.isEnabled = false 

                if (!isLiked) {
                    holder.binding.ivLike.tag = "liked"
                    holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
                    holder.binding.tvLikesCount.text = (currentLikes + 1).toString()
                    likedIds.add(id ?: "")
                } else {
                    holder.binding.ivLike.tag = "unliked"
                    holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
                    holder.binding.tvLikesCount.text = (if (currentLikes > 0) currentLikes - 1 else 0).toString()
                    likedIds.remove(id ?: "")
                }

                lifecycleScope.launch {
                    try {
                        val type = if (isRecipe) "receta" else "publicacion"
                        repository.toggleLike(currentUid, id ?: "", type)
                        
                        // Sincronizar número real tras el trigger
                        val updated = if (isRecipe) repository.getRecetaById(id ?: "")?.totalLikes else {
                             repository.getFeedPublicaciones().find { it.id == id }?.totalLikes
                        }
                        updated?.let { holder.binding.tvLikesCount.text = it.toString() }
                    } catch (e: Exception) {
                        Log.e("RecetApp", "Error Like: ${e.message}")
                        notifyItemChanged(holder.adapterPosition)
                    } finally {
                        holder.binding.ivLike.isEnabled = true
                    }
                }
            }

            holder.binding.ivComment.setOnClickListener {
                showComments(id ?: "", if (isRecipe) "receta" else "publicacion")
            }

            holder.itemView.setOnClickListener {
                if (isRecipe) {
                    val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", id)
                    startActivity(intent)
                }
            }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemRecipeFeedBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
