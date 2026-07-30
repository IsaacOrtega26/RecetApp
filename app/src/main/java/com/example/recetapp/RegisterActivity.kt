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
import com.example.recetapp.R

import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            if (validateForm(name, email, password, etFullName, etEmail, etPassword)) {
                lifecycleScope.launch {
                    try {
                        Log.d("RecetApp", "Registrando usuario: $email")
                        // 1. Registro en Supabase Auth
                        val authResponse = try {
                            SupabaseConfig.client.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        } catch (e: Exception) {
                            if (e.message?.contains("already registered", ignoreCase = true) == true) {
                                Log.d("RecetApp", "El usuario ya existe en Auth, intentando recuperar perfil...")
                                null // Continuamos para intentar guardar el perfil si falta
                            } else {
                                throw e
                            }
                        }

                        // 2. Intentar guardar en la tabla 'usuarios'
                        // Obtenemos el ID ya sea del registro nuevo o de la sesión actual
                        val userId = authResponse?.id ?: SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                        
                        if (userId != null) {
                            val handle = email.substringBefore("@").lowercase().replace(".", "_")
                            val nuevoUsuario = Usuario(
                                uid = userId,
                                email = email,
                                nombreUsuario = handle,
                                nombreCompleto = name,
                                contrasena = password,
                                rol = "usuario",
                                totalRecetas = 0,
                                activo = true
                            )
                            
                            Log.d("RecetApp", "Guardando perfil en tabla usuarios: $handle")
                            val isSaved = repository.insertUsuario(nuevoUsuario)
                            
                            if (isSaved) {
                                Toast.makeText(this@RegisterActivity, "¡Cuenta creada y perfil guardado!", Toast.LENGTH_SHORT).show()
                                navigateToMain()
                            } else {
                                Log.e("RecetApp", "Fallo al insertar en tabla 'usuarios'. Probablemente RLS.")
                                Toast.makeText(this@RegisterActivity, "Error de base de datos. Verifica permisos RLS.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // Si no hay ID y no se pudo registrar, es que algo falló antes
                            Toast.makeText(this@RegisterActivity, "El usuario ya existe. Por favor, inicia sesión.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("RecetApp", "ERROR EN REGISTRO", e)
                        Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun validateForm(
        name: String, 
        email: String, 
        password: String,
        etName: EditText,
        etEmail: EditText,
        etPass: EditText
    ): Boolean {
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
        } else if (password.length < 8) {
            etPass.error = "Mínimo 8 caracteres"
            isValid = false
        }

        return isValid
    }
}
