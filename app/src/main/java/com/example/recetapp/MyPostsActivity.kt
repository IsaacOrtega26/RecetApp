package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.ActivityMyPostsBinding
import com.example.recetapp.databinding.ItemPostManageBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MyPostsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyPostsBinding
    private val repository = SupabaseConfig.repository
    private val currentUserId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMyPostsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener { loadPosts() }

        setupRecyclerView()
        loadPosts()
    }

    private fun setupRecyclerView() {
        binding.rvMyPosts.layoutManager = LinearLayoutManager(this)
    }

    private fun loadPosts() {
        val uid = currentUserId ?: return
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            try {
                val myPosts = repository.getPublicacionesByAutor(uid)
                binding.swipeRefresh.isRefreshing = false
                
                if (myPosts.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvMyPosts.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.rvMyPosts.visibility = View.VISIBLE
                    binding.rvMyPosts.adapter = PostAdapter(myPosts)
                }
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                Toast.makeText(this@MyPostsActivity, "Error al cargar publicaciones", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePost(post: Publicacion) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar publicación")
            .setMessage("¿Estás seguro de que quieres eliminar esta publicación?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    val success = repository.deletePublicacion(post.id ?: "", currentUserId ?: "")
                    if (success) {
                        Toast.makeText(this@MyPostsActivity, "Publicación eliminada", Toast.LENGTH_SHORT).show()
                        loadPosts()
                    } else {
                        Toast.makeText(this@MyPostsActivity, "Error al eliminar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    inner class PostAdapter(private val list: List<Publicacion>) : RecyclerView.Adapter<PostAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(ItemPostManageBinding.inflate(LayoutInflater.from(p.context), p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = list[pos]
            h.binding.tvDescription.text = item.descripcion
            h.binding.tvLikesCount.text = "${item.totalLikes} likes"
            
            // Cargar imagen del post
            lifecycleScope.launch {
                val images = repository.getImagenesPublicacion(item.id ?: "")
                if (images.isNotEmpty()) {
                    h.binding.ivPostImage.visibility = View.VISIBLE
                    h.binding.ivPostImage.load(images.first())
                } else {
                    h.binding.ivPostImage.visibility = View.GONE
                }
            }

            h.binding.btnDelete.setOnClickListener {
                deletePost(item)
            }
        }
        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemPostManageBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
