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

class FollowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var userId: String? = null
    private var mode = 0 // 0: Followers, 1: Following

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        lifecycleScope.launch {
            val users = if (mode == 0) repository.getFollowers(uid) else repository.getFollowing(uid)
            binding.rvUsers.adapter = UserAdapter(users)
        }
    }

    inner class UserAdapter(private val users: List<Usuario>) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemUserSearchBinding.inflate(layoutInflater, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val user = users[pos]
            h.binding.tvName.text = user.nombreCompleto ?: user.nombreUsuario
            h.binding.tvHandle.text = "@${user.nombreUsuario}"
            user.fotoUrl?.let { h.binding.ivAvatar.load(it) }
            h.itemView.setOnClickListener {
                val intent = Intent(this@FollowListActivity, MainActivity::class.java)
                intent.putExtra("EXTRA_START_TAB", "OTHER_PROFILE")
                intent.putExtra("USER_ID", user.uid)
                // Note: MainActivity should handle this. Or just open another Activity if it's better.
                // For now, let's just finish and let the user navigate. 
                // Actually, a ProfileActivity would be better than reusing MainActivity.
                startActivity(intent)
            }
        }
        override fun getItemCount() = users.size
        inner class ViewHolder(val binding: ItemUserSearchBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
