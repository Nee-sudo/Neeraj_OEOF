package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ApiClient
import com.example.data.AppDatabase
import com.example.data.NotificationEntity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.i("MyFCMService", "🔄 onNewToken triggered. Token coordinate: $token")
        
        // Cache token in shared preferences
        val sharedPrefs = applicationContext.getSharedPreferences("one_earth_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_registration_token", token).apply()

        serviceScope.launch {
            try {
                // Initialize database
                val db = AppDatabase.getDatabase(applicationContext)
                val userDao = db.userDao()
                val loggedUser = userDao.getUserById("me")

                if (loggedUser != null) {
                    // Update locally
                    userDao.insertUser(loggedUser.copy(fcmToken = token))

                    // Sync to remote backend
                    val backendUrl = sharedPrefs.getString("backend_url", ApiClient.getBaseUrl()) ?: ApiClient.getBaseUrl()
                    ApiClient.updateBaseUrl(backendUrl)
                    ApiClient.authToken = loggedUser.token

                    val identifier = loggedUser.email.lowercase().trim()
                    ApiClient.getService().updateProfileFields(identifier, mapOf("fcmToken" to token))
                    android.util.Log.i("MyFCMService", "Successfully updated FCM registration token on server!")
                }
            } catch (e: Exception) {
                android.util.Log.e("MyFCMService", "Error syncing new token to server: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        android.util.Log.i("MyFCMService", "✉️ onMessageReceived triggered.")

        val data = remoteMessage.data
        val title = data["title"] ?: remoteMessage.notification?.title ?: "One Earth Notification"
        val body = data["body"] ?: remoteMessage.notification?.body ?: "Civic Update Alert"
        val notifId = data["notif_id"] ?: "${System.currentTimeMillis()}"
        val type = data["deep_link_type"] ?: "general"
        val roomId = data["room_id"]?.toIntOrNull()
        val postId = data["post_id"]?.toIntOrNull()
        val userId = data["user_id"]
        val badge = data["badge_count"]?.toIntOrNull() ?: 1

        android.util.Log.i("MyFCMService", "Parsed Push payload: ID=$notifId, Type=$type, roomId=$roomId, postId=$postId, userId=$userId")

        // 1. Persist notification in local Room Database dynamically
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entity = NotificationEntity(
                    id = notifId,
                    recipientId = "me",
                    senderId = "",
                    type = type,
                    title = title,
                    body = body,
                    isRead = false,
                    createdAt = System.currentTimeMillis(),
                    senderName = "System",
                    senderFlag = "🌍",
                    messageText = body,
                    timestamp = System.currentTimeMillis(),
                    roomId = roomId,
                    postId = postId,
                    userId = userId
                )
                db.notificationDao().insertNotification(entity)
                android.util.Log.i("MyFCMService", "FCM Notification auto-inserted into local database storage.")
            } catch (dbErr: Exception) {
                android.util.Log.e("MyFCMService", "Local DB push insertion error: ${dbErr.message}")
            }
        }

        // 2. Display System Notification
        showNotification(title, body, type, roomId, postId, userId, badge)
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String,
        roomId: Int?,
        postId: Int?,
        userId: String?,
        badgeCount: Int
    ) {
        val channelId = "high_importance_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "One Earth Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-importance production push notifications"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep-linking intent mapping using intent extras
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link_type", type)
            if (roomId != null) putExtra("room_id", roomId)
            if (postId != null) putExtra("post_id", postId)
            if (userId != null) putExtra("user_id", userId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // Find system icon resource
        val iconRes = applicationContext.resources.getIdentifier(
            "ic_notification", "drawable", applicationContext.packageName
        ).let { if (it == 0) android.R.drawable.sym_def_app_icon else it }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(badgeCount) // system-level badge badge counter support

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
