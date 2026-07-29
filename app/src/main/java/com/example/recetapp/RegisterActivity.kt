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

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_main)) { v, insets ->
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
                Toast.makeText(this, "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
            }
        }
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
