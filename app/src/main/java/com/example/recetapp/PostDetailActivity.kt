package com.example.recetapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.recetapp.databinding.ActivityPostDetailBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PostDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        postId = intent.getStringExtra("POST_ID")
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadPostData()
    }

    private fun loadPostData() {
        postId?.let { id ->
            lifecycleScope.launch {
                try {
                    val post = repository.getPublicacionById(id) ?: return@launch
                    binding.tvDescription.text = post.descripcion
                    
                    val likes = repository.getLikeCount(id)
                    binding.tvLikes.text = likes.toString()

                    val images = repository.getImagenesPublicacion(id)
                    if (images.isNotEmpty()) {
                        binding.ivPostDetail.load(images[0])
                    }

                    val author = repository.getUsuarioByUid(post.autorUid)
                    author?.let { u ->
                        binding.tvAuthorName.text = u.nombreCompleto ?: u.nombreUsuario
                        u.fotoUrl?.let { binding.ivAuthorAvatar.load(it) }
                    }
                    
                    loadComments(id)
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error loading post detail", e)
                }
            }
        }
    }

    private fun loadComments(resId: String) {
        // Implement simple comment list if needed, or reuse MainActivity's dialog logic
    }
}
