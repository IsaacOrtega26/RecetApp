package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reset_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = findViewById<EditText>(R.id.etConfirmPassword)
        val btnReset = findViewById<Button>(R.id.btnResetPassword)
        val progress = findViewById<ProgressBar>(R.id.progressBar)

        btnReset.setOnClickListener {
            val password = etPassword.text.toString().trim()
            val confirm = etConfirm.text.toString().trim()

            if (password.length < 8) {
                etPassword.error = "Mínimo 8 caracteres"
                return@setOnClickListener
            }
            if (password != confirm) {
                etConfirm.error = "Las contraseñas no coinciden"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    btnReset.isEnabled = false
                    progress.visibility = View.VISIBLE
                    
                    // Actualizamos en Supabase Auth
                    SupabaseConfig.client.auth.updateUser {
                        this.password = password
                    }
                    
                    // Nota: Si se requiere actualizar en la tabla 'usuarios' manualmente, 
                    // se podría añadir aquí el repository.updateUsuario si el UID está disponible.
                    
                    Toast.makeText(this@ResetPasswordActivity, "Contraseña cambiada con éxito", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@ResetPasswordActivity, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al resetear clave", e)
                    Toast.makeText(this@ResetPasswordActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnReset.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }
}
