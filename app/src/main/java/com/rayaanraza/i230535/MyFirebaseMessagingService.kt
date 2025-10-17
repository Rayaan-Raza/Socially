package com.rayaanraza.i230535

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

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")

        // Save token to Firebase Database
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("fcmToken")
                .setValue(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FCM", "Message received from: ${message.from}")

        // Check if message contains a notification payload
        message.notification?.let {
            Log.d("FCM", "Notification Title: ${it.title}")
            Log.d("FCM", "Notification Body: ${it.body}")
        }

        // Check if message contains data payload
        if (message.data.isNotEmpty()) {
            Log.d("FCM", "Message data: ${message.data}")
            handleDataMessage(message.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return

        when (type) {
            "message" -> handleMessageNotification(data)
            "like" -> handleLikeNotification(data)
            "comment" -> handleCommentNotification(data)
            "follow" -> handleFollowNotification(data)
        }
    }

    private fun handleMessageNotification(data: Map<String, String>) {
        val senderName = data["senderName"] ?: "Someone"
        val chatId = data["chatId"] ?: ""
        val senderId = data["senderId"] ?: ""

        // Extract user IDs from chatId
        val userIds = chatId.split("-")
        val otherUserId = if (userIds[0] == senderId) userIds[0] else userIds.getOrNull(1) ?: senderId

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("userId", otherUserId)
            putExtra("username", senderName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = senderName,
            message = "New message",
            intent = intent,
            channelId = "messages",
            notificationId = chatId.hashCode()
        )
    }

    private fun handleLikeNotification(data: Map<String, String>) {
        val postId = data["postId"] ?: ""
        val userId = data["userId"] ?: ""

        val intent = Intent(this, home_page::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "New Like",
            message = "Someone liked your post",
            intent = intent,
            channelId = "social",
            notificationId = postId.hashCode()
        )
    }

    private fun handleCommentNotification(data: Map<String, String>) {
        val postId = data["postId"] ?: ""

        val intent = Intent(this, home_page::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "New Comment",
            message = "Someone commented on your post",
            intent = intent,
            channelId = "social",
            notificationId = postId.hashCode()
        )
    }

    private fun handleFollowNotification(data: Map<String, String>) {
        val userId = data["userId"] ?: ""

        val intent = Intent(this, home_page::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "New Follower",
            message = "Someone started following you",
            intent = intent,
            channelId = "social",
            notificationId = userId.hashCode()
        )
    }

    private fun showNotification(
        title: String,
        message: String,
        intent: Intent,
        channelId: String,
        notificationId: Int
    ) {
        createNotificationChannel(channelId)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = when (channelId) {
                "messages" -> "Messages"
                "calls" -> "Calls"
                "social" -> "Social"
                else -> "Notifications"
            }

            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for $channelName"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}