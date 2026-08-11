package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class RegisterActivity : AppCompatActivity() {

    private val supabase by lazy { SupabaseConfig.client }
    private val repository by lazy { SupabaseRepository(supabase) }
    private var isBusy = false

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

        findViewById<Button>(R.id.btnGoogleRegister).setOnClickListener {
            signInWithGoogle()
        }

        setupRegistration()
    }

    private fun setupRegistration() {
        val etFullName = findViewById<EditText>(R.id.etFullName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleRegister)
        val progressBar = findViewById<View>(R.id.progressBar)

        btnRegister.setOnClickListener {
            if (isBusy) return@setOnClickListener
            val name = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateForm(name, email, password, etFullName, etEmail, etPassword)) return@setOnClickListener

            setLoadingState(isLoading = true, btnRegister, btnGoogle, progressBar)

            lifecycleScope.launch {
                try {
                    isBusy = true
                    Log.d("RecetApp", "Registrando usuario: $email")
                    
                    // 1. Registro en Auth
                    val response = supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    // 2. Crear perfil en tabla 'usuarios'
                    val userId = response?.id ?: supabase.auth.currentUserOrNull()?.id
                    if (userId != null) {
                        val handle = email.substringBefore("@").lowercase().replace(".", "_")
                        val nuevoUsuario = Usuario(
                            uid = userId,
                            email = email,
                            nombreUsuario = handle,
                            nombreCompleto = name,
                            rol = "usuario",
                            totalRecetas = 0,
                            activo = true,
                        )
                        repository.insertUsuario(nuevoUsuario)
                    }

                    withContext(Dispatchers.Main) {
                        ToastManager.showToast(this@RegisterActivity, "¡Cuenta creada con éxito!")
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error en registro", e)
                    withContext(Dispatchers.Main) {
                        isBusy = false
                        ToastManager.showToast(this@RegisterActivity, "Error: ${e.message}")
                        setLoadingState(isLoading = false, btnRegister, btnGoogle, progressBar)
                    }
                }
            }
        }
    }

    private fun signInWithGoogle() {
        if (isBusy) return
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleRegister)
        val progressBar = findViewById<View>(R.id.progressBar)

        val credentialManager = CredentialManager.create(this)
        val webClientId = "637493199596-gju7dpo8t5qfgv2252sotgc7a74873mu.apps.googleusercontent.com"

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        setLoadingState(isLoading = true, btnRegister, btnGoogle, progressBar)

        lifecycleScope.launch {
            try {
                isBusy = true
                Log.d("RecetApp", "DEBUG-REG: Iniciando con WebClientID: $webClientId")
                val result = credentialManager.getCredential(this@RegisterActivity, request)
                val credential = result.credential
                Log.d("RecetApp", "Credencial obtenida: ${credential.type}")

                if (credential is CustomCredential &&
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    supabase.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }

                    // Crear perfil si es nuevo
                    val user = supabase.auth.currentSessionOrNull()?.user
                    val userId = user?.id
                    if (userId != null) {
                        val email = user.email ?: ""
                        val metadata = user.userMetadata
                        
                        val name = metadata?.get("full_name")?.toString()?.replace("\"", "")
                            ?: metadata?.get("name")?.toString()?.replace("\"", "")
                            ?: ""
                            
                        val photoUrl = metadata?.get("avatar_url")?.toString()?.replace("\"", "")
                            ?: metadata?.get("picture")?.toString()?.replace("\"", "")
                        
                        val handle = email.substringBefore("@").lowercase().replace(".", "_")
                        
                        val existingByEmail = repository.getUsuarioByEmail(email)

                        val usuarioSync = Usuario(
                            uid = userId,
                            email = email,
                            nombreUsuario = existingByEmail?.nombreUsuario ?: handle,
                            nombreCompleto = if (existingByEmail?.nombreCompleto.isNullOrBlank()) name else existingByEmail?.nombreCompleto,
                            fotoUrl = photoUrl ?: existingByEmail?.fotoUrl,
                            rol = existingByEmail?.rol ?: "usuario",
                            activo = true,
                        )
                        repository.upsertUsuario(usuarioSync)
                    }

                    withContext(Dispatchers.Main) {
                        isBusy = false
                        ToastManager.showToast(this@RegisterActivity, "¡Bienvenido con Google!")
                        kotlinx.coroutines.delay(100L)
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    isBusy = false
                    val errorType = e.javaClass.simpleName
                    val errorMsg = e.message ?: "Sin mensaje"
                    
                    Log.e("RecetApp", "FALLO TOTAL GOOGLE REGISTER: [$errorType]")
                    Log.e("RecetApp", "MENSAJE: $errorMsg")
                    
                    val userFriendlyMsg = when {
                        e is NoCredentialException -> "No se encontraron cuentas"
                        errorMsg.contains("cancelled", ignoreCase = true) -> null
                        errorMsg.contains("Configuration not found", ignoreCase = true) -> "Configuración no encontrada en Google Cloud"
                        else -> "Error: $errorType"
                    }
                    
                    userFriendlyMsg?.let { ToastManager.showToast(this@RegisterActivity, it, isLong = true) }
                    setLoadingState(isLoading = false, btnRegister, btnGoogle, progressBar)
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, primaryBtn: Button, secondaryBtn: Button, progress: View?) {
        primaryBtn.isEnabled = !isLoading
        secondaryBtn.isEnabled = !isLoading
        primaryBtn.alpha = if (isLoading) 0.7f else 1.0f
        secondaryBtn.alpha = if (isLoading) 0.7f else 1.0f
        progress?.visibility = if (isLoading) View.VISIBLE else View.GONE
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

}
