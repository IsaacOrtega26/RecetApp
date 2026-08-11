package com.example.recetapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

class LoginActivity : AppCompatActivity() {

    private val supabase by lazy { SupabaseConfig.client }
    private val repository by lazy { SupabaseRepository(supabase) }
    private var isBusy = false

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

        findViewById<Button>(R.id.btnGoogleLogin).setOnClickListener {
            signInWithGoogle()
        }

        setupLogin()
    }

    private fun setupLogin() {
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginSubmit)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            if (isBusy) return@setOnClickListener
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (!validateForm(email, password, etEmail, etPassword)) return@setOnClickListener

            setLoadingState(isLoading = true, btnLogin, btnGoogle, progressBar)

            lifecycleScope.launch {
                try {
                    isBusy = true
                    Log.d("RecetApp", "Intentando iniciar sesión para: $email")
                    
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    Log.d("RecetApp", "Sesión iniciada con éxito")
                    withContext(Dispatchers.Main) {
                        ToastManager.showToast(this@LoginActivity, "¡Bienvenido de nuevo!")
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                } catch (e: Exception) {
                    Log.e("RecetApp", "Error de autenticación", e)
                    withContext(Dispatchers.Main) {
                        isBusy = false
                        val errorMsg = when {
                            e.message?.contains("Invalid login credentials") == true -> "Correo o contraseña incorrectos"
                            e.message?.contains("Email not confirmed") == true -> "Debes confirmar tu correo"
                            else -> "Error: ${e.message}"
                        }
                        ToastManager.showToast(this@LoginActivity, errorMsg, true)
                        setLoadingState(false, btnLogin, btnGoogle, progressBar)
                    }
                }
            }
        }
    }

    private fun signInWithGoogle() {
        if (isBusy) return
        val btnLogin = findViewById<Button>(R.id.btnLoginSubmit)
        val btnGoogle = findViewById<Button>(R.id.btnGoogleLogin)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

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

        setLoadingState(isLoading = true, btnLogin, btnGoogle, progressBar)

        lifecycleScope.launch {
            try {
                isBusy = true
                Log.d("RecetApp", "DEBUG: Iniciando flow con WebClientID: $webClientId")
                Log.d("RecetApp", "DEBUG: PackageName: $packageName")
                
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val credential = result.credential
                Log.d("RecetApp", "Credencial obtenida: ${credential.type}")

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    supabase.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }

                    // --- NUEVA LÓGICA: ASEGURAR PERFIL ---
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
                        
                        // Buscamos si ya existe por email para evitar el conflicto de duplicados
                        val existingByEmail = repository.getUsuarioByEmail(email)
                        
                        val usuarioSync = Usuario(
                            uid = userId, // Actualizamos al nuevo UID de la sesión actual
                            email = email,
                            nombreUsuario = existingByEmail?.nombreUsuario ?: handle,
                            nombreCompleto = if (existingByEmail?.nombreCompleto.isNullOrBlank()) name else existingByEmail?.nombreCompleto,
                            fotoUrl = photoUrl ?: existingByEmail?.fotoUrl,
                            rol = existingByEmail?.rol ?: "usuario",
                            activo = true,
                        )
                        val success = repository.upsertUsuario(usuarioSync)
                        if (!success) {
                            Log.w("RecetApp", "No se pudo sincronizar el perfil con Google (RLS o conflictos). Intentando continuar...")
                        }
                    }

                    withContext(Dispatchers.Main) {
                        isBusy = false
                        ToastManager.showToast(this@LoginActivity, "¡Bienvenido con Google!")
                        kotlinx.coroutines.delay(100L)
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finishAffinity()
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    isBusy = false
                    val errorType = e.javaClass.simpleName
                    val errorMsg = e.message ?: "Sin mensaje"
                    
                    Log.e("RecetApp", "FALLO TOTAL GOOGLE LOGIN: [$errorType]")
                    Log.e("RecetApp", "MENSAJE: $errorMsg")
                    
                    val userFriendlyMsg = when {
                        e is NoCredentialException -> "No se encontraron cuentas (asegúrate de tener una cuenta de Google activa en el emulador)"
                        errorMsg.contains("cancelled", ignoreCase = true) -> null // Cancelado por usuario
                        errorMsg.contains("Configuration not found", ignoreCase = true) -> "Configuración no encontrada en Google Cloud (revisa el SHA-1)"
                        else -> "Error de conexión con Google: $errorType"
                    }
                    
                    userFriendlyMsg?.let { ToastManager.showToast(this@LoginActivity, it, isLong = true) }
                    setLoadingState(false, btnLogin, btnGoogle, progressBar)
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean, primaryBtn: Button, secondaryBtn: Button, progress: View) {
        primaryBtn.isEnabled = !isLoading
        secondaryBtn.isEnabled = !isLoading
        primaryBtn.alpha = if (isLoading) 0.7f else 1.0f
        secondaryBtn.alpha = if (isLoading) 0.7f else 1.0f
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

}
