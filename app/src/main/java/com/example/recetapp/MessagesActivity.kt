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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var messagesJob: kotlinx.coroutines.Job? = null
    private val conversationsList = mutableListOf<Mensaje>()
    private lateinit var adapter: ConversationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadConversations()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    private fun loadConversations() {
        val currentUid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id ?: return
        
        adapter = ConversationAdapter(conversationsList, currentUid)
        binding.rvConversations.adapter = adapter

        lifecycleScope.launch {
            try {
                val allMessages = repository.getConversaciones(currentUid)
                updateLocalConversations(allMessages, currentUid)
                
                // Iniciar escucha en tiempo real
                startRealtimeConversations(currentUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateLocalConversations(allMessages: List<Mensaje>, currentUid: String) {
        val grouped = allMessages.groupBy { 
            if (it.emisorUid == currentUid) it.receptorUid else it.emisorUid 
        }.map { it.value.first() }
        
        conversationsList.clear()
        conversationsList.addAll(grouped)
        adapter.notifyDataSetChanged()
    }

    private fun startRealtimeConversations(currentUid: String) {
        messagesJob?.cancel()
        messagesJob = lifecycleScope.launch {
            repository.listenToAllMessages(currentUid, this).collect {
                // Al recibir CUALQUIER mensaje nuevo, refrescamos la lista de agrupados
                // (Es lo más sencillo para mantener el orden y el "último mensaje")
                val allMessages = repository.getConversaciones(currentUid)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateLocalConversations(allMessages, currentUid)
                }
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
