package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.ActivityFollowListBinding
import com.example.recetapp.databinding.ItemUserSearchBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.jan.supabase.auth.auth
import android.view.View
import android.widget.Toast

class FollowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var userId: String? = null
    private var mode = 0 // 0: Followers, 1: Following

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Añadimos 60px extra para bajar el encabezado y liberar botones
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        userId = intent.getStringExtra("USER_ID")
        mode = intent.getIntExtra("MODE", 0)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tabLayout.getTabAt(mode)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                mode = tab?.position ?: 0
                loadUsers()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadUsers()
    }

    private fun loadUsers() {
        val uid = userId ?: return
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: ""
        lifecycleScope.launch {
            val users = if (mode == 0) repository.getFollowers(uid) else repository.getFollowing(uid)
            val followingIds = repository.getFollowing(currentUid).mapNotNull { it.uid }.toSet()
            binding.rvUsers.adapter = UserAdapter(users, followingIds)
        }
    }

    inner class UserAdapter(private val users: List<Usuario>, private val followingIds: Set<String>) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemUserSearchBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val user = users[pos]
            val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
            
            h.binding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
            h.binding.tvHandle.text = "@${user.nombreUsuario}"
            user.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            
            // Lógica de botones de acción para mi propio perfil o listas ajenas
            if (userId == currentUid) {
                h.binding.btnUnfollow.visibility = View.VISIBLE
                if (mode == 1) {
                    // Pestaña "Siguiendo" propia: botón "Dejar de seguir"
                    h.binding.btnUnfollow.text = "Dejar de seguir"
                    h.binding.btnUnfollow.setOnClickListener {
                        h.binding.btnUnfollow.isEnabled = false
                        lifecycleScope.launch {
                            try {
                                repository.unfollowUser(currentUid ?: "", user.uid ?: "")
                                loadUsers() 
                                Toast.makeText(this@FollowListActivity, "Dejaste de seguir a ${user.nombreUsuario}", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                h.binding.btnUnfollow.isEnabled = true
                                Toast.makeText(this@FollowListActivity, "Error al procesar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // Pestaña "Seguidores" propia: botón "Eliminar" (quitar de mis seguidores)
                    // Además, si no lo sigo yo a él, mostrar botón "Seguir" (HU-Seguimiento Mutuo)
                    val isFollowingBack = followingIds.contains(user.uid)
                    
                    if (!isFollowingBack && user.uid != currentUid) {
                        h.binding.btnUnfollow.text = "Seguir también"
                        h.binding.btnUnfollow.setOnClickListener {
                            h.binding.btnUnfollow.isEnabled = false
                            lifecycleScope.launch {
                                val success = repository.followUser(currentUid ?: "", user.uid ?: "")
                                if (success) {
                                    loadUsers()
                                    Toast.makeText(this@FollowListActivity, "Siguiendo a ${user.nombreUsuario}", Toast.LENGTH_SHORT).show()
                                } else {
                                    h.binding.btnUnfollow.isEnabled = true
                                }
                            }
                        }
                    } else {
                        h.binding.btnUnfollow.text = "Eliminar"
                        h.binding.btnUnfollow.setOnClickListener {
                            h.binding.btnUnfollow.isEnabled = false
                            lifecycleScope.launch {
                                try {
                                    repository.unfollowUser(user.uid ?: "", currentUid ?: "")
                                    loadUsers()
                                    Toast.makeText(this@FollowListActivity, "Has eliminado a ${user.nombreUsuario} de tus seguidores", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    h.binding.btnUnfollow.isEnabled = true
                                    Toast.makeText(this@FollowListActivity, "Error al eliminar seguidor", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            } else {
                // Listas de otros usuarios: Mostrar botón "Seguir" si no los sigo
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
                                if (success) loadUsers()
                            }
                        }
                    }
                } else {
                    h.binding.btnUnfollow.visibility = View.GONE
                }
            }

            h.itemView.setOnClickListener {
                val intent = Intent(this@FollowListActivity, MainActivity::class.java)
                intent.putExtra("EXTRA_START_TAB", "OTHER_PROFILE")
                intent.putExtra("USER_ID", user.uid)
                startActivity(intent)
            }
        }
        override fun getItemCount() = users.size
        inner class ViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
