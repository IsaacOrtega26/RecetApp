package com.example.recetapp

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recetapp.databinding.ActivitySettingsBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top + 60, systemBars.right, 0)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        // HU-06: Cambiar Privacidad
        binding.switchPublic.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                uid?.let {
                    repository.updatePrivacidad(it, isChecked)
                    ToastManager.showToast(this@SettingsActivity, "Privacidad actualizada")
                }
            }
        }

        // HU-07: Borrar cuenta
        binding.btnDeleteAccount.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("¿Eliminar cuenta?")
                .setMessage("Esta acción no se puede deshacer. Perderás todas tus recetas y seguidores.")
                .setPositiveButton("Eliminar") { _, _ ->
                    lifecycleScope.launch {
                        val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                        uid?.let {
                            repository.deleteCuenta(it)
                            SupabaseConfig.client.auth.signOut()
                            finishAffinity() // Cierra todas las actividades
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}
