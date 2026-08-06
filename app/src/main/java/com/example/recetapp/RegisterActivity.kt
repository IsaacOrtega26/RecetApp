package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class RegisterActivity : AppCompatActivity() {

    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        
        val rootView = findViewById<View>(R.id.register_main)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tvFooter).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupRegistration()
    }

    private fun setupRegistration() {
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val name = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateForm(name, email, password, etFullName, etEmail, etPassword)) return@setOnClickListener

            lifecycleScope.launch {
                try {
                    Log.d("RecetApp", "Registrando usuario: $email")
                    
                    // 1. Registro en Auth
                    SupabaseConfig.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    // 2. Crear perfil en tabla 'usuarios'
                    val userId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                    if (userId != null) {
                        val handle = email.substringBefore("@").lowercase().replace(".", "_")
                        val nuevoUsuario = Usuario(
                            uid = userId,
                            email = email,
                            nombreUsuario = handle,
                            nombreCompleto = name,
                            rol = "usuario",
                            totalRecetas = 0,
                            activo = true
                        )
                        repository.insertUsuario(nuevoUsuario)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error en registro", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun validateForm(name: String, email: String, password: String, etName: EditText, etEmail: EditText, etPass: EditText): Boolean {
        var isValid = true
        if (name.isEmpty()) {
            etName.error = "El nombre es obligatorio"
            isValid = false
        }
        if (email.isEmpty()) {
            etEmail.error = "El correo es obligatorio"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Correo no válido"
            isValid = false
        }
        if (password.isEmpty()) {
            etPass.error = "La contraseña es obligatoria"
            isValid = false
        } else if (password.length < 6) { // Supabase default min is 6
            etPass.error = "Mínimo 6 caracteres"
            isValid = false
        }
        return isValid
    }

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
