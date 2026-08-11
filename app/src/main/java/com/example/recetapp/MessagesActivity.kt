package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.ActivityMessagesBinding
import com.example.recetapp.databinding.ItemConversationBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class MessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadConversations()
    }

    private fun loadConversations() {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        lifecycleScope.launch {
            try {
                val allMessages = repository.getConversaciones(currentUid)
                
                // Agrupar por el otro usuario
                val conversations = allMessages.groupBy { 
                    if (it.emisorUid == currentUid) it.receptorUid else it.emisorUid 
                }.map { entry ->
                    // Nos quedamos con el último mensaje de cada conversación
                    entry.value.first()
                }

                binding.rvConversations.adapter = ConversationAdapter(conversations, currentUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class ConversationAdapter(
        private val list: List<Mensaje>,
        private val currentUid: String
    ) : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
            ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val otherUid = if (item.emisorUid == currentUid) item.receptorUid else item.emisorUid
            
            holder.binding.tvLastMessage.text = item.contenido
            holder.binding.tvTime.text = item.fechaCreacion?.take(10) // Simplificado

            lifecycleScope.launch {
                val otherUser = repository.getUsuarioByUid(otherUid)
                holder.binding.tvName.text = otherUser?.nombreCompleto ?: otherUser?.nombreUsuario ?: "Usuario"
                otherUser?.fotoUrl?.let { holder.binding.ivAvatar.load(it) }
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MessagesActivity, ChatActivity::class.java)
                intent.putExtra("OTHER_USER_ID", otherUid)
                startActivity(intent)
            }
        }

        override fun getItemCount() = list.size
        inner class ViewHolder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
