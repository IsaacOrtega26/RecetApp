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
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val repository = SupabaseRepository(SupabaseConfig.client)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        
        val rootView = findViewById<View>(R.id.login_main)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.tvFooter).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        setupLogin()
    }

    private fun setupLogin() {
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginSubmit)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "El correo es obligatorio"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "La contraseña es obligatoria"
                return@setOnClickListener
            }

            setLoadingState(true, btnLogin, progressBar)

            lifecycleScope.launch {
                try {
                    Log.d("RecetApp", "--- INICIO LOGIN ---")
                    
                    withTimeout(40000) { 
                        try {
                            // 1. Intento de Autenticación oficial
                            Log.d("RecetApp", "Paso 1: Validando credenciales en Auth...")
                            SupabaseConfig.client.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                        } catch (e: Exception) {
                            // SI FALLA POR CONFIRMACIÓN: Bypass por Base de Datos
                            if (e.message?.contains("Email not confirmed", ignoreCase = true) == true) {
                                Log.d("RecetApp", "Email no confirmado detectado. Iniciando Bypass...")
                                val profile = repository.getUsuarioByEmail(email)
                                if (profile != null && profile.contrasena == password) {
                                    Log.d("RecetApp", "Bypass exitoso: Contraseña coincide en BD.")
                                    // Permitimos el paso aunque no haya sesión oficial (MainActivity cargará por email)
                                    withContext(Dispatchers.Main) {
                                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                        intent.putExtra("email_forced", email)
                                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        startActivity(intent)
                                        finish()
                                    }
                                    return@withTimeout
                                } else {
                                    throw e // Si no coincide la contraseña en BD, lanzamos el error original
                                }
                            } else {
                                throw e // Otros errores de Auth
                            }
                        }
                        
                        // 2. Si el Auth oficial funcionó, cargamos perfil normal
                        Log.d("RecetApp", "Auth oficial exitoso. Recuperando perfil...")
                        var profile = repository.getUsuarioByEmail(email)

                        // AUTO-CREACIÓN DE PERFIL: Si el usuario existe en Auth pero no en BD
                        if (profile == null) {
                            Log.d("RecetApp", "Usuario sin perfil detectado. Creando perfil de emergencia...")
                            val userId = SupabaseConfig.client.auth.currentSessionOrNull()?.user?.id
                            if (userId != null) {
                                val handle = email.substringBefore("@").lowercase().replace(".", "_")
                                val baseProfile = Usuario(
                                    uid = userId,
                                    email = email,
                                    nombreUsuario = handle,
                                    nombreCompleto = handle.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    contrasena = password,
                                    rol = "usuario",
                                    totalRecetas = 0,
                                    activo = true
                                )
                                repository.insertUsuario(baseProfile)
                                profile = baseProfile
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (profile != null) {
                                Toast.makeText(this@LoginActivity, "¡Bienvenido, ${profile.nombreCompleto ?: profile.nombreUsuario}!", Toast.LENGTH_SHORT).show()
                            }
                            
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                        }
                    }
                } catch (e: HttpRequestTimeoutException) {
                    Log.e("RecetApp", "ERROR DE RED: Timeout de Supabase (30s)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "El servidor tarda demasiado. Verifica tu internet.", Toast.LENGTH_LONG).show()
                        setLoadingState(false, btnLogin, progressBar)
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "ERROR EN LOGIN: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        val message = if (e.message?.contains("Email not confirmed", ignoreCase = true) == true) {
                            "Por favor, confirma tu correo electrónico antes de entrar."
                        } else if (e.message?.contains("Invalid login credentials", ignoreCase = true) == true) {
                            "Correo o contraseña incorrectos."
                        } else {
                            "Error: ${e.message}"
                        }
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                        setLoadingState(false, btnLogin, progressBar)
                    }
                } finally {
                    Log.d("RecetApp", "--- FIN PROCESO LOGIN ---")
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, button: Button, progress: View) {
        button.isEnabled = !isLoading
        button.alpha = if (isLoading) 0.5f else 1.0f
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun validateForm(
        email: String, 
        password: String,
        etEmail: EditText,
        etPass: EditText
    ): Boolean {
        var isValid = true
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
        }
        return isValid
    }
}
