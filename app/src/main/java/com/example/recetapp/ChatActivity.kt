package com.example.recetapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.recetapp.databinding.ActivityChatBinding
import com.example.recetapp.databinding.ItemMessageMeBinding
import com.example.recetapp.databinding.ItemMessageOtherBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var otherUserId: String? = null
    private var currentUserId: String? = null
    private var chatJob: kotlinx.coroutines.Job? = null
    private val messagesList = mutableListOf<Mensaje>()
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        otherUserId = intent.getStringExtra("OTHER_USER_ID")
        currentUserId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadOtherUserInfo()
        loadMessages()

        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun loadOtherUserInfo() {
        otherUserId?.let { id ->
            lifecycleScope.launch {
                val user = repository.getUsuarioByUid(id)
                binding.toolbar.title = user?.nombreCompleto ?: user?.nombreUsuario ?: "Chat"
            }
        }
    }

    private fun loadMessages() {
        val miUid = currentUserId ?: return
        val otroUid = otherUserId ?: return
        
        // Configurar adaptador inicial
        adapter = MessageAdapter(messagesList, miUid)
        binding.rvMessages.adapter = adapter
        
        lifecycleScope.launch {
            try {
                val list = repository.getMensajesConUsuario(miUid, otroUid)
                messagesList.clear()
                messagesList.addAll(list)
                adapter.notifyDataSetChanged()
                binding.rvMessages.scrollToPosition(messagesList.size - 1)
                
                // Iniciar escucha en tiempo real
                startRealtimeChat(miUid, otroUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startRealtimeChat(miUid: String, otroUid: String) {
        chatJob?.cancel()
        chatJob = lifecycleScope.launch {
            repository.listenToMessages(miUid, otroUid, this).collect { newMsg ->
                // Evitar duplicados si el mensaje ya se añadió localmente al enviar
                if (messagesList.none { it.id == newMsg.id && it.id != null }) {
                    messagesList.add(newMsg)
                    withContext(Dispatchers.Main) {
                        adapter.notifyItemInserted(messagesList.size - 1)
                        binding.rvMessages.scrollToPosition(messagesList.size - 1)
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val miUid = currentUserId ?: return
        val otroUid = otherUserId ?: return
        val content = binding.etMessage.text.toString().trim()
        
        if (content.isNotEmpty()) {
            binding.btnSend.isEnabled = false
            lifecycleScope.launch {
                val success = repository.sendMensaje(miUid, otroUid, content)
                withContext(Dispatchers.Main) {
                    if (success) {
                        binding.etMessage.setText("")
                        // El mensaje aparecerá solo vía Realtime para evitar duplicidad visual
                    }
                    binding.btnSend.isEnabled = true
                }
            }
        }
    }

    inner class MessageAdapter(
        private val list: List<Mensaje>,
        private val currentUid: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_ME = 1
        private val TYPE_OTHER = 2

        override fun getItemViewType(position: Int): Int {
            return if (list[position].emisorUid == currentUid) TYPE_ME else TYPE_OTHER
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_ME) {
                MeViewHolder(ItemMessageMeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            } else {
                OtherViewHolder(ItemMessageOtherBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = list[position]
            if (holder is MeViewHolder) {
                holder.binding.tvContent.text = item.contenido
            } else if (holder is OtherViewHolder) {
                holder.binding.tvContent.text = item.contenido
            }
        }

        override fun getItemCount() = list.size

        inner class MeViewHolder(val binding: ItemMessageMeBinding) : RecyclerView.ViewHolder(binding.root)
        inner class OtherViewHolder(val binding: ItemMessageOtherBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
