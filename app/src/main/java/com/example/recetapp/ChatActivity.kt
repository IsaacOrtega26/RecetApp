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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)
    private var otherUserId: String? = null
    private var currentUserId: String? = null
    private var adapter: MessageAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        otherUserId = intent.getStringExtra("OTHER_USER_ID")
        currentUserId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadOtherUserInfo()
        loadInitialMessages()
        startRealtimeListener()
        checkRecipeShare()

        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun checkRecipeShare() {
        val recipeId = intent.getStringExtra("RECIPE_ID")
        val recipeName = intent.getStringExtra("RECIPE_NAME")
        if (recipeId != null && recipeName != null) {
            val shareText = "¡Hola! Mira esta receta: $recipeName\n(Enlace: recetapp://recipe/$recipeId)"
            binding.etMessage.setText(shareText)
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(mutableListOf(), currentUserId ?: "")
        binding.rvMessages.adapter = adapter
    }

    private fun loadOtherUserInfo() {
        otherUserId?.let { id ->
            lifecycleScope.launch {
                val user = repository.getUsuarioByUid(id)
                binding.toolbar.title = user?.nombreCompleto ?: user?.nombreUsuario ?: "Chat"
            }
        }
    }

    private fun loadInitialMessages() {
        val miUid = currentUserId ?: return
        val otroUid = otherUserId ?: return
        lifecycleScope.launch {
            try {
                val list = repository.getMensajesConUsuario(miUid, otroUid)
                adapter?.updateList(list)
                binding.rvMessages.scrollToPosition(list.size - 1)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startRealtimeListener() {
        val miUid = currentUserId ?: return
        val otroUid = otherUserId ?: return
        lifecycleScope.launch {
            repository.listenToMessages(miUid, otroUid, this).collect { msg ->
                withContext(Dispatchers.Main) {
                    adapter?.addMessage(msg)
                    binding.rvMessages.smoothScrollToPosition(adapter!!.itemCount - 1)
                }
            }
        }
    }

    private fun sendMessage() {
        val miUid = currentUserId ?: return
        val otroUid = otherUserId ?: return
        val content = binding.etMessage.text.toString().trim()
        
        if (content.isNotEmpty()) {
            binding.etMessage.setText("")
            lifecycleScope.launch {
                repository.sendMensaje(miUid, otroUid, content)
            }
        }
    }

    inner class MessageAdapter(
        private val list: MutableList<Mensaje>,
        private val currentUid: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_ME = 1
        private val TYPE_OTHER = 2

        fun updateList(newList: List<Mensaje>) {
            list.clear()
            list.addAll(newList)
            notifyDataSetChanged()
        }

        fun addMessage(msg: Mensaje) {
            if (!list.any { it.id == msg.id && it.id != null }) {
                list.add(msg)
                notifyItemInserted(list.size - 1)
            }
        }

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
