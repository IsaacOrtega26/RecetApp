package com.example.recetapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recetapp.databinding.ActivityCreatePostBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.ivPostImage.setImageURI(it)
            binding.ivPostImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.ivPostImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnPublish.setOnClickListener {
            publishPost()
        }
    }

    private fun publishPost() {
        val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        val desc = binding.etPostDescription.text.toString().trim()

        if (selectedImageUri == null && desc.isEmpty()) {
            Toast.makeText(this, "Añade una foto o descripción", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPublish.isEnabled = false
        binding.btnPublish.text = "Publicando..."

        lifecycleScope.launch {
            try {
                val newPost = Publicacion(
                    autorUid = uid,
                    descripcion = desc,
                    visibilidad = "publica"
                )
                val inserted = repository.insertPublicacion(newPost)
                
                if (inserted?.id != null) {
                    selectedImageUri?.let { uri ->
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val fileName = "post_${inserted.id}.jpg"
                            val url = repository.uploadImage("posts", fileName, bytes)
                            if (url != null) {
                                repository.insertImagenPublicacion(inserted.id!!, url)
                            }
                        }
                    }
                    Toast.makeText(this@CreatePostActivity, "¡Publicado!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.btnPublish.isEnabled = true
                binding.btnPublish.text = "Publicar"
            }
        }
    }
}
