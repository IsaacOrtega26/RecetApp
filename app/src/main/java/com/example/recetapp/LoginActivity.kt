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

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
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

            if (!validateForm(email, password, etEmail, etPassword)) return@setOnClickListener

            setLoadingState(true, btnLogin, progressBar)

            lifecycleScope.launch {
                try {
                    Log.d("RecetApp", "Intentando iniciar sesión para: $email")
                    
                    SupabaseConfig.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    Log.d("RecetApp", "Sesión iniciada con éxito")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "¡Bienvenido de nuevo!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error de autenticación", e)
                    withContext(Dispatchers.Main) {
                        val errorMsg = when {
                            e.message?.contains("Invalid login credentials") == true -> "Correo o contraseña incorrectos"
                            e.message?.contains("Email not confirmed") == true -> "Debes confirmar tu correo"
                            else -> "Error: ${e.message}"
                        }
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                        setLoadingState(false, btnLogin, progressBar)
                    }
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, button: Button, progress: View) {
        button.isEnabled = !isLoading
        button.alpha = if (isLoading) 0.7f else 1.0f
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun validateForm(email: String, password: String, etEmail: EditText, etPass: EditText): Boolean {
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

    private fun hideSystemUI() {
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
    }
}
