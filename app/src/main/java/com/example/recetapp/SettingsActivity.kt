package com.example.recetapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recetapp.databinding.ActivitySettingsBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // HU-06: Cambiar Privacidad
        binding.switchPublic.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val uid = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                uid?.let {
                    repository.updatePrivacidad(it, isChecked)
                    Toast.makeText(this@SettingsActivity, "Privacidad actualizada", Toast.LENGTH_SHORT).show()
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
