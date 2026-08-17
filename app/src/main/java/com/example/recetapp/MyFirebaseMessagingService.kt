package com.example.recetapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("RecetApp_FCM", "Nuevo Token: $token")
        
        // Si el usuario está logueado, actualizar el token en Supabase
        val user = SupabaseConfig.client.auth.currentSessionOrNull()?.user
        user?.id?.let { uid ->
            scope.launch {
                val repo = SupabaseRepository(SupabaseConfig.client)
                repo.updateFcmToken(uid, token)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("RecetApp_FCM", "Mensaje recibido de: ${remoteMessage.from}")

        // Mostrar notificación si tiene cuerpo
        remoteMessage.notification?.let {
            showNotification(it.title ?: "RecetApp", it.body ?: "")
        } ?: run {
            // Manejar mensajes de datos si es necesario
            val title = remoteMessage.data["title"] ?: "RecetApp"
            val body = remoteMessage.data["body"] ?: ""
            if (body.isNotEmpty()) {
                showNotification(title, body)
            }
        }
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "recetapp_notifications"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notificaciones de RecetApp", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Puedes cambiar por un icono vectorial más adelante
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}
