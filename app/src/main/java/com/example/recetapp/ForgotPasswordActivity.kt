package com.example.recetapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnSend = findViewById<Button>(R.id.btnSendResetLink)
        val progress = findViewById<ProgressBar>(R.id.progressBar)

        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Ingresa tu correo"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    btnSend.isEnabled = false
                    progress.visibility = View.VISIBLE
                    
                    SupabaseConfig.client.auth.resetPasswordForEmail(email)
                    
                    ToastManager.showToast(this@ForgotPasswordActivity, "Enlace enviado. Revisa tu correo.", isLong = true)
                    finish()
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error al enviar reset", e)
                    ToastManager.showToast(this@ForgotPasswordActivity, "Error: ${e.message}")
                } finally {
                    btnSend.isEnabled = true
                    progress.visibility = View.GONE
                }
            }
        }
    }
}
