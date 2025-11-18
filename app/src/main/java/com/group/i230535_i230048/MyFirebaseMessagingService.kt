package com.group.i230535_i230048

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        const val CALL_CHANNEL_ID = "calls"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 New FCM token: $token")

        // Get current user ID from SharedPreferences and update token on MySQL backend
        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (userId.isNotEmpty()) {
            updateFcmTokenOnServer(userId, token)
        }

        // Save token locally
        prefs.edit().putString("fcm_token", token).apply()
    }

    private fun updateFcmTokenOnServer(userId: String, token: String) {
        val queue = Volley.newRequestQueue(this)
        val url = AppGlobals.BASE_URL + "user_update_fcm.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        Log.d(TAG, "✅ FCM token updated on server")
                    } else {
                        Log.e(TAG, "❌ Failed to update token: ${json.optString("message")}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response: ${e.message}")
                }
            },
            { error ->
                Log.e(TAG, "Network error updating token: ${error.message}")
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "uid" to userId,
                    "fcmToken" to token
                )
            }
        }
        queue.add(request)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "📨 Message received from: ${message.from}")

        // Check if message contains a notification payload
        message.notification?.let {
            Log.d(TAG, "Notification Title: ${it.title}")
            Log.d(TAG, "Notification Body: ${it.body}")
        }

        // Check if message contains data payload
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data: ${message.data}")
            handleDataMessage(message.data)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return

        when (type) {
            // Call-related messages
            "INCOMING_CALL" -> handleIncomingCall(data)
            "CALL_ENDED" -> handleCallEnded(data)
            "CALL_ANSWERED" -> handleCallAnswered(data)
            "CALL_DECLINED" -> handleCallDeclined(data)

            // Other messages (existing)
            "message" -> handleMessageNotification(data)
            "screenshot" -> handleScreenshotNotification(data)
            "follow_request" -> handleFollowRequestNotification(data)
            "follow_accepted" -> handleFollowAcceptedNotification(data)
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val callerUid = data["callerUid"] ?: return
        val callerName = data["callerName"] ?: "Unknown"
        val callType = data["callType"] ?: "audio"
        val channelName = data["channelName"] ?: callId

        Log.d(TAG, "📞 Incoming $callType call from $callerName")
        Log.d(TAG, "Call ID: $callId, Channel: $channelName")

        // Create intent for incoming call screen
        val intent = Intent(this, IncomingCall::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("CALLER_UID", callerUid)
            putExtra("CALLER_NAME", callerName)
            putExtra("IS_VIDEO_CALL", callType == "video")
        }

        // Show full-screen notification for incoming call
        showIncomingCallNotification(callId, callerName, callType, intent)

        // Try to start activity directly (works when app is in foreground)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IncomingCall activity: ${e.message}")
        }
    }

    private fun handleCallEnded(data: Map<String, String>) {
        val callId = data["callId"] ?: return
        val reason = data["reason"] ?: "cancelled"

        Log.d(TAG, "📴 Call ended: $callId, reason: $reason")

        // Cancel the call notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(callId.hashCode())

        // Broadcast to close any open call screens
        val intent = Intent("com.group.i230535_i230048.CALL_ENDED").apply {
            putExtra("callId", callId)
            putExtra("reason", reason)
        }
        sendBroadcast(intent)
    }

    private fun handleCallAnswered(data: Map<String, String>) {
        val callId = data["callId"] ?: return

        Log.d(TAG, "✅ Call answered: $callId")

        // Broadcast to caller's app that call was answered
        val intent = Intent("com.group.i230535_i230048.CALL_ANSWERED").apply {
            putExtra("callId", callId)
        }
        sendBroadcast(intent)
    }

    private fun handleCallDeclined(data: Map<String, String>) {
        val callId = data["callId"] ?: return

        Log.d(TAG, "❌ Call declined: $callId")

        // Broadcast to caller's app that call was declined
        val intent = Intent("com.group.i230535_i230048.CALL_DECLINED").apply {
            putExtra("callId", callId)
        }
        sendBroadcast(intent)
    }

    private fun showIncomingCallNotification(
        callId: String,
        callerName: String,
        callType: String,
        fullScreenIntent: Intent
    ) {
        createCallNotificationChannel()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept call action
        val acceptIntent = Intent(this, IncomingCall::class.java).apply {
            action = "ACCEPT_CALL"
            putExtra("CALL_ID", callId)
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 1, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline call action
        val declineIntent = Intent(this, IncomingCall::class.java).apply {
            action = "DECLINE_CALL"
            putExtra("CALL_ID", callId)
        }
        val declinePendingIntent = PendingIntent.getActivity(
            this, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callTypeText = if (callType == "video") "Video" else "Voice"

        val notification = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming $callTypeText Call")
            .setContentText("$callerName is calling...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .build()

        notificationManager.notify(callId.hashCode(), notification)
    }

    private fun createCallNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming voice and video calls"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ========== EXISTING NOTIFICATION HANDLERS ==========

    private fun handleMessageNotification(data: Map<String, String>) {
        val senderName = data["senderName"] ?: "Someone"
        val chatId = data["chatId"] ?: ""
        val senderId = data["senderId"] ?: ""

        val userIds = chatId.split("_")
        val otherUserId = if (userIds.getOrNull(0) == senderId) userIds[0] else userIds.getOrNull(1) ?: senderId

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

    private fun handleScreenshotNotification(data: Map<String, String>) {
        val takerId = data["takerId"] ?: ""
        val chatId = data["chatId"] ?: ""

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        val intent = Intent(this, ChatActivity::class.java).apply {
            val userIds = chatId.split("_")
            val otherUserId = userIds.firstOrNull { it != currentUserId } ?: takerId
            putExtra("userId", otherUserId)
            putExtra("username", "User")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "📸 Screenshot Alert!",
            message = "Someone took a screenshot in your chat",
            intent = intent,
            channelId = "security",
            notificationId = chatId.hashCode()
        )
    }

    private fun handleFollowRequestNotification(data: Map<String, String>) {
        val requesterId = data["requesterId"] ?: ""
        val requesterName = data["requesterName"] ?: "Someone"

        val intent = Intent(this, you_page::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "New Follow Request",
            message = "$requesterName wants to follow you",
            intent = intent,
            channelId = "social",
            notificationId = requesterId.hashCode()
        )
    }

    private fun handleFollowAcceptedNotification(data: Map<String, String>) {
        val userId = data["userId"] ?: ""
        val userName = data["userName"] ?: "Someone"

        val intent = Intent(this, view_profile::class.java).apply {
            putExtra("userId", userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = "Follow Request Accepted",
            message = "$userName accepted your follow request",
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
                "security" -> "Security Alerts"
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