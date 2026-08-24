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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.realtime
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import coil.load
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val supabase = SupabaseConfig.client
    private val repository = SupabaseRepository(supabase)
    private var currentUser: Usuario? = null

    private lateinit var mainBinding: ActivityMainBinding
    private var currentViewState = "FEED" // "FEED", "EXPLORE", "NOTIF", "PROFILE", "OTHER_PROFILE", "EDIT_PROFILE", "CHATS"
    private var notificationJob: kotlinx.coroutines.Job? = null
    private var requestsJob: kotlinx.coroutines.Job? = null

    private val pickProfileImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { updateProfilePreview(it) }
    }

    private var profileImageUri: android.net.Uri? = null
    private fun updateProfilePreview(uri: android.net.Uri) {
        profileImageUri = uri
        mainBinding.contentFrame.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.ivEditAvatar)?.setImageURI(uri)
    }

    private val requestNotificationPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("RecetApp", "Permiso de notificaciones concedido")
        } else {
            Log.w("RecetApp", "Permiso de notificaciones denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkSession()
        setupBackNavigation()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        // Si el usuario está viendo su propio perfil, refrescar datos para actualizar contadores
        if (currentViewState == "PROFILE") {
            showProfile(supabase.auth.currentSessionOrNull()?.user?.id)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
        lifecycleScope.launch {
            try {
                // Esperar hasta que el estado de la sesión sea definitivo (no 'Initializing')
                val status = supabase.auth.sessionStatus.filter { s -> s !is SessionStatus.Initializing }.first()
                
                if (status is SessionStatus.Authenticated) {
                    Log.d("RecetApp", "Sesión persistente detectada: ${status.session.user?.email}")
                    setupMainShell()
                } else {
                    Log.d("RecetApp", "No hay sesión persistente, mostrando bienvenida")
                    showWelcome()
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected when activity is finishing or backgrounded
                Log.d("RecetApp", "Verificación de sesión cancelada")
            } catch (ex: Exception) {
                Log.e("RecetApp", "Error al verificar sesión", ex)
                showWelcome()
            }
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

        // Manejo de Insets para que el encabezado no se corte con la barra de estado
        ViewCompat.setOnApplyWindowInsetsListener(mainBinding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

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
            
            // Conectar a Realtime
            lifecycleScope.launch {
                try {
                    supabase.realtime.connect()
                    Log.d("RecetApp", "Realtime conectado")
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al conectar Realtime", e)
                }
            }
        } catch (e: Exception) {
            Log.e("RecetApp", "Error binding navigation", e)
        }

        val startTab = intent.getStringExtra("EXTRA_START_TAB")
        if (startTab == "PROFILE") {
            mainBinding.bottomNavigation?.selectedItemId = R.id.nav_profile
            showProfile(supabase.auth.currentSessionOrNull()?.user?.id)
        } else {
            showFeed()
        }

        startRealtimeNotifications()
        startRealtimeRequests()
        // syncFcmToken() // Commented out until google-services.json is added
    }

    /* // Commented out until google-services.json is added
    private fun syncFcmToken() {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("RecetApp_FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("RecetApp_FCM", "Token actual: $token")
            
            lifecycleScope.launch {
                repository.updateFcmToken(uid, token)
            }
        }
    }
    */

    private fun startRealtimeRequests() {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        requestsJob?.cancel()
        requestsJob = lifecycleScope.launch {
            repository.listenToFollowRequests(uid, this).collect {
                withContext(Dispatchers.Main) {
                    ToastManager.showToast(this@MainActivity, "¡Has recibido una nueva solicitud de seguimiento!", isLong = true)
                    // No refrescamos la lista aquí porque suele ser un diálogo separado,
                    // pero el usuario ya está avisado.
                }
            }
        }
    }

    private fun startRealtimeNotifications() {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        notificationJob?.cancel()
        notificationJob = lifecycleScope.launch {
            try {
                Log.d("RecetApp", "Iniciando escucha de notificaciones en tiempo real para $uid...")
                repository.listenToNotifications(uid, this).collect { notif ->
                    Log.d("RecetApp", "Notificación recibida: ${notif.tipo}")
                    withContext(Dispatchers.Main) {
                        showNotificationAlert(notif)
                    }
                }
            } catch (e: Exception) {
                Log.e("RecetApp", "Error en listener de notificaciones", e)
            }
        }
    }

    private fun showNotificationAlert(notif: Notificacion) {
        val message = when (notif.tipo) {
            "like" -> "¡A alguien le gustó tu receta!"
            "comentario" -> "Tienes un nuevo comentario"
            "seguidor" -> "¡Tienes un nuevo seguidor!"
            "solicitud" -> "Has recibido una solicitud de seguimiento"
            "solicitud_aceptada" -> "Tu solicitud de seguimiento fue aceptada"
            "mensaje" -> "Nuevo mensaje recibido"
            else -> "Nueva actividad en tu cuenta"
        }
        
        ToastManager.showToast(this, message, isLong = true)
        showSystemNotification("RecetApp", message)
        
        // Si estamos en la pestaña de notificaciones, refrescar la lista
        if (currentViewState == "NOTIF") {
            showNotifications()
        }
    }

    private fun showSystemNotification(title: String, message: String) {
        val channelId = "recetapp_general"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notificaciones Generales", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
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
            } catch (_: Exception) {
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
        
        private val followingIds = mutableSetOf<String>()

        fun updateUsers(newUsers: List<Usuario>) {
            users = newUsers
            fetchFollowing()
            notifyDataSetChanged()
        }

        private fun fetchFollowing() {
            val currentUid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
            lifecycleScope.launch {
                val following = repository.getFollowing(currentUid)
                followingIds.clear()
                followingIds.addAll(following.mapNotNull { it.uid })
                notifyDataSetChanged()
            }
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemUserSearchBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val user = users[pos]
            val currentUid = supabase.auth.currentSessionOrNull()?.user?.id
            
            h.binding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
            h.binding.tvHandle.text = "@${user.nombreUsuario}"
            user.fotoUrl?.let { h.binding.ivAvatar.load(it) }

            // Mostrar botón Seguir si no soy yo
            if (user.uid != currentUid) {
                val isFollowing = followingIds.contains(user.uid)
                h.binding.btnUnfollow.visibility = View.VISIBLE
                h.binding.btnUnfollow.text = if (isFollowing) "Siguiendo" else "Seguir"
                h.binding.btnUnfollow.alpha = if (isFollowing) 0.6f else 1.0f
                h.binding.btnUnfollow.isEnabled = !isFollowing
                
                h.binding.btnUnfollow.setOnClickListener {
                    if (!isFollowing) {
                        lifecycleScope.launch {
                            val success = repository.followUser(currentUid ?: "", user.uid ?: "")
                            if (success) fetchFollowing()
                        }
                    }
                }
            } else {
                h.binding.btnUnfollow.visibility = View.GONE
            }

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
                "solicitud_aceptada" -> "aceptó tu solicitud de seguimiento"
                else -> "interactuó contigo"
            }
            h.binding.tvContent.text = text
            h.itemView.setOnClickListener {
                if (n.tipo == "mensaje") {
                    val intent = Intent(this@MainActivity, ChatActivity::class.java)
                    intent.putExtra("OTHER_USER_ID", n.actorUid)
                    startActivity(intent)
                } else if (n.tipo == "like" || n.tipo == "comentario") {
                    // Determinar si el objetoId es receta o post
                    // Por ahora asumimos según el contexto o buscamos
                    lifecycleScope.launch {
                        val recipe = n.objetoId?.let { repository.getRecetaById(it) }
                        if (recipe != null) {
                            val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                            intent.putExtra("RECIPE_ID", n.objetoId)
                            startActivity(intent)
                        } else {
                            val intent = Intent(this@MainActivity, PostDetailActivity::class.java)
                            intent.putExtra("POST_ID", n.objetoId)
                            startActivity(intent)
                        }
                    }
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

    private fun showComments(recetaId: String, tipo: String = ResourceTypes.RECIPE, onCountChanged: (Int) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val b = DialogCommentsBinding.inflate(layoutInflater)
        dialog.setContentView(b.root)

        b.btnCloseComments.setOnClickListener { dialog.dismiss() }

        fun loadComments() {
            lifecycleScope.launch {
                val comments = repository.getComentarios(recetaId)
                b.rvComments.adapter = CommentAdapter(comments) { 
                    loadComments()
                }
                // Notificar al feed el nuevo número de comentarios
                onCountChanged(comments.size)
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
                    profileBinding.tvRecipeCount.text = user.totalRecetas.coerceAtLeast(0).toString()
                    profileBinding.tvFollowersCount.text = user.totalSeguidores.coerceAtLeast(0).toString()
                    profileBinding.tvFollowingCount.text = user.totalSeguidos.coerceAtLeast(0).toString()
                    
                    user.fotoUrl?.let { profileBinding.ivAvatar.load(it) }
                    profileBinding.rvRecipes.adapter = RecipeGridAdapter(recipes)

                    profileBinding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                        override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                            if (tab?.position == 1) { // Publicaciones
                                if (isOwnProfile) {
                                    startActivity(Intent(this@MainActivity, MyPostsActivity::class.java))
                                    profileBinding.tabLayout.getTabAt(0)?.select()
                                } else {
                                    // Para perfiles ajenos podrías cargar sus posts aquí si quieres
                                    lifecycleScope.launch {
                                        val posts = repository.getPublicacionesByAutor(user.uid ?: "")
                                        // Podrías necesitar un adaptador de posts para el grid o lista
                                        Toast.makeText(this@MainActivity, "Cargando ${posts.size} publicaciones", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                profileBinding.rvRecipes.adapter = RecipeGridAdapter(recipes)
                            }
                        }
                        override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                        override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                            if (tab?.position == 1 && isOwnProfile) {
                                startActivity(Intent(this@MainActivity, MyPostsActivity::class.java))
                            }
                        }
                    })

                    // Navegar a lista de seguidores/seguidos
                    profileBinding.llFollowers.setOnClickListener {
                        val intent = Intent(this@MainActivity, FollowListActivity::class.java)
                        intent.putExtra("USER_ID", user.uid)
                        intent.putExtra("MODE", 0)
                        startActivity(intent)
                    }
                    profileBinding.llFollowing.setOnClickListener {
                        val intent = Intent(this@MainActivity, FollowListActivity::class.java)
                        intent.putExtra("USER_ID", user.uid)
                        intent.putExtra("MODE", 1)
                        startActivity(intent)
                    }

                    // Lógica de Botón Seguir (perfil ajeno)
                    if (!isOwnProfile) {
                        fun refreshFollowStatus() {
                            lifecycleScope.launch {
                                val state = repository.getEstadoSeguimiento(sessionUser?.id ?: "", user.uid ?: "")
                                profileBinding.btnFollowProfile.text = when(state) {
                                    "siguiendo" -> "Siguiendo"
                                    "pendiente" -> "Solicitud enviada"
                                    else -> "Seguir"
                                }
                                profileBinding.btnFollowProfile.alpha = if (state == "ninguno") 1.0f else 0.6f
                                profileBinding.btnFollowProfile.isEnabled = state != "pendiente"
                                
                                // Recargar datos de usuario para actualizar contador
                                val updatedUser = repository.getUsuarioByUid(user.uid!!)
                                updatedUser?.let {
                                    profileBinding.tvFollowersCount.text = it.totalSeguidores.coerceAtLeast(0).toString()
                                }
                            }
                        }

                        refreshFollowStatus()

                        profileBinding.btnFollowProfile.setOnClickListener {
                            lifecycleScope.launch {
                                val state = repository.getEstadoSeguimiento(sessionUser?.id ?: "", user.uid ?: "")
                                if (state == "siguiendo") {
                                    repository.unfollowUser(sessionUser?.id ?: "", user.uid ?: "")
                                } else if (state == "ninguno") {
                                    val success = repository.followUser(sessionUser?.id ?: "", user.uid ?: "")
                                    if (!success) {
                                        Toast.makeText(this@MainActivity, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                refreshFollowStatus()
                            }
                        }
                    }

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

                    // ADMIN PANEL VISIBILITY - Solo visible en el perfil propio si es admin
                    val sessionUserUid = sessionUser?.id
                    if (isOwnProfile && (user.rol.lowercase() == "administrador" || user.rol.lowercase() == "admin")) {
                        profileBinding.btnAdminPanel.visibility = View.VISIBLE
                        profileBinding.btnAdminPanel.setOnClickListener {
                            val intent = Intent(this@MainActivity, AdminPanelActivity::class.java)
                            startActivity(intent)
                        }
                    } else {
                        profileBinding.btnAdminPanel.visibility = View.GONE
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
                // Usamos una lista mutable para el adaptador
                b.rvFollowRequests.adapter = FollowRequestAdapter(requests.toMutableList()) { 
                    loadRequests()
                    // Refrescar perfil para actualizar contadores
                    if (currentViewState == "PROFILE") showProfile(uid)
                }
            }
        }

        loadRequests()
        dialog.show()
    }

    inner class FollowRequestAdapter(
        private val list: MutableList<SolicitudSeguimiento>,
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
                
                // IU OPTIMISTA: Eliminar de la lista local inmediatamente
                val currentPos = h.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    list.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                }

                lifecycleScope.launch {
                    val success = repository.aceptarSolicitud(solicitudId, item.solicitanteUid, item.destinoUid)
                    if (!success) {
                        // Si falla, recargar la lista real (el diálogo se mantiene abierto)
                        onHandled()
                        Toast.makeText(this@MainActivity, "Error al aceptar la solicitud", Toast.LENGTH_SHORT).show()
                    } else {
                        // Sincronizar contadores en segundo plano
                        onHandled()
                    }
                }
            }
            h.binding.btnReject.setOnClickListener {
                val solicitudId = item.id ?: return@setOnClickListener
                // IU OPTIMISTA
                val currentPos = h.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    list.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                }

                lifecycleScope.launch {
                    val success = repository.rechazarSolicitud(solicitudId)
                    if (!success) {
                        onHandled()
                        Toast.makeText(this@MainActivity, "Error al rechazar la solicitud", Toast.LENGTH_SHORT).show()
                    } else {
                        onHandled()
                    }
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
                val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                intent.putExtra("RECIPE_ID", recipe.id)
                startActivity(intent)
            }
        }
        override fun getItemCount() = recipes.size
        inner class ViewHolder(val binding: ItemRecipeGridBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class RecipeFeedAdapter(private var items: List<Any>) : RecyclerView.Adapter<RecipeFeedAdapter.ViewHolder>() {
        
        private val likedIds = mutableSetOf<String>()
        private var isFetchingLikes = false

        fun updateItems(newItems: List<Any>) {
            items = newItems
            if (!isFetchingLikes) fetchLikes()
            notifyDataSetChanged()
        }
        
        private fun fetchLikes() {
            val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
            isFetchingLikes = true
            lifecycleScope.launch {
                try {
                    val ids = repository.getLikedResourceIds(uid)
                    likedIds.clear()
                    likedIds.addAll(ids)
                    withContext(Dispatchers.Main) {
                        notifyDataSetChanged() // Refrescar corazones con datos reales
                    }
                } catch (e: Exception) { 
                    Log.e("RecetApp", "Error al refrescar likes: ${e.message}")
                } finally {
                    isFetchingLikes = false
                }
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
                holder.binding.tvDifficulty.text = getString(R.string.difficulty_format, r.dificultad?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Media")
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
            holder.binding.tvLikesCount.text = totalLikes.coerceAtLeast(0).toString()
            holder.binding.tvCommentsCount.text = totalComentarios.coerceAtLeast(0).toString()

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
                
                // Bloqueo inmediato para evitar doble petición
                holder.binding.ivLike.isEnabled = false 

                // Cambio visual instantáneo (Optimista)
                if (!isLiked) {
                    holder.binding.ivLike.tag = "liked"
                    holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
                    holder.binding.tvLikesCount.text = (currentLikes + 1).toString()
                    likedIds.add(id ?: "")
                } else {
                    holder.binding.ivLike.tag = "unliked"
                    holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
                    holder.binding.tvLikesCount.text = (currentLikes - 1).coerceAtLeast(0).toString()
                    likedIds.remove(id ?: "")
                }

                lifecycleScope.launch {
                    try {
                        val type = if (isRecipe) ResourceTypes.RECIPE else ResourceTypes.POST
                        repository.toggleLike(currentUid, id ?: "", type)
                        
                        // Sincronizar número real contando directamente en la tabla de likes
                        // Esto evita depender de disparadores (triggers) que puedan fallar
                        val realLikeCount = repository.getLikeCount(id ?: "")
                        
                        withContext(Dispatchers.Main) {
                            holder.binding.tvLikesCount.text = realLikeCount.toString()
                        }
                    } catch (e: Exception) {
                        Log.e("RecetApp", "Error al procesar Like: ${e.message}")
                        // Revertir cambio visual en caso de error de red
                        withContext(Dispatchers.Main) {
                            if (isLiked) {
                                holder.binding.ivLike.tag = "liked"
                                holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.orange_primary))
                                holder.binding.tvLikesCount.text = currentLikes.coerceAtLeast(0).toString()
                                likedIds.add(id ?: "")
                            } else {
                                holder.binding.ivLike.tag = "unliked"
                                holder.binding.ivLike.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_primary))
                                holder.binding.tvLikesCount.text = currentLikes.coerceAtLeast(0).toString()
                                likedIds.remove(id ?: "")
                            }
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            holder.binding.ivLike.isEnabled = true
                        }
                    }
                }
            }

            holder.binding.ivComment.setOnClickListener {
                showComments(id ?: "", if (isRecipe) ResourceTypes.RECIPE else ResourceTypes.POST) { newCount ->
                    holder.binding.tvCommentsCount.text = newCount.coerceAtLeast(0).toString()
                }
            }

            holder.itemView.setOnClickListener {
                if (isRecipe) {
                    val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", id)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@MainActivity, PostDetailActivity::class.java)
                    intent.putExtra("POST_ID", id)
                    startActivity(intent)
                }
            }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemRecipeFeedBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
