package com.example.recetapp

import android.content.Intent
import android.os.Bundle
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

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        
        val mainView = findViewById<View>(R.id.login_main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
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

        setupLogin()
    }

    private fun setupLogin() {
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginSubmit)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateForm(email, password, etEmail, etPassword)) {
                Toast.makeText(this, "¡Sesión iniciada!", Toast.LENGTH_SHORT).show()
                // Lógica de autenticación futura
            }
        }
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
