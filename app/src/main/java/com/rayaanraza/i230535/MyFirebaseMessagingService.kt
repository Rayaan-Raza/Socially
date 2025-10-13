//package com.rayaanraza.i230535
//
//// --- FIX: Add all necessary and correct imports ---
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.core.content.getSystemService
//import androidx.preference.isNotEmpty
//import androidx.privacysandbox.tools.core.generator.build
//import com.google.firebase.auth.ktx.auth
//import com.google.firebase.database.ktx.database
//import com.google.firebase.ktx.Firebase
//import com.google.firebase.messaging.FirebaseMessagingService
//import com.google.firebase.messaging.RemoteMessage
//import kotlin.random.Random
//// --- End of import fixes ---
//
//class MyFirebaseMessagingService : FirebaseMessagingService() {
//
//    // Use a companion object for constants
//    companion object {
//        private const val TAG = "FCM_Service"
//        private const val CHANNEL_ID = "socially_notifications"
//        private const val CHANNEL_NAME = "Socially Notifications"
//    }
//
//    /**
//     * Called when a new FCM registration token is generated.
//     * This is the perfect place to update the token on your server.
//     */
//    override fun onNewToken(token: String) {
//        super.onNewToken(token)
//        Log.d(TAG, "New FCM token generated: $token")
//        updateTokenInFirebase(token)
//    }
//
//    /**
//     * Called when a message is received.
//     * This is where you process incoming push notifications.
//     */
//    override fun onMessageReceived(message: RemoteMessage) {
//        super.onMessageReceived(message)
//        Log.d(TAG, "Message received from: ${message.from}")
//
//        // It's best practice to handle the data payload as it gives you more control.
//        if (message.data.isNotEmpty()) {
//            Log.d(TAG, "Message data payload: ${message.data}")
//            // Generate a notification from the data payload
//            showNotificationFromData(message.data)
//        } else {
//            // Fallback for simple notifications sent from the Firebase Console
//            message.notification?.let {
//                Log.d(TAG, "Notification Body: ${it.body}")
//                showNotification(it.title ?: "Socially", it.body ?: "", emptyMap())
//            }
//        }
//    }
//
//    /**
//     * Creates and shows a simple notification containing the received FCM message.
//     */
//    private fun showNotificationFromData(data: Map<String, String>) {
//        val title = data["title"] ?: "Socially"
//        val body = data["body"] ?: "You have a new notification."
//        showNotification(title, body, data)
//    }
//
//    private fun showNotification(title: String, body: String, data: Map<String, String>) {
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        // For Android Oreo (API 26) and above, you must create a notification channel.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                CHANNEL_NAME,
//                NotificationManager.IMPORTANCE_HIGH
//            ).apply {
//                description = "Notifications for messages and social activity."
//                enableVibration(true)
//            }
//            notificationManager.createNotificationChannel(channel)
//        }
//
//        // Create the PendingIntent that will open the correct screen when the notification is tapped.
//        val intent = createIntentForNotification(data)
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            Random.nextInt(), // Use a random request code to ensure intents are unique
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        // Build the notification using NotificationCompat for backward compatibility.
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_notification) // **IMPORTANT**: Make sure you have 'ic_notification.xml' in your res/drawable
//            .setContentTitle(title)
//            .setContentText(body)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setAutoCancel(true) // Dismiss the notification when the user taps on it
//            .setContentIntent(pendingIntent)
//            .build()
//
//        // Show the notification. Use a random ID to show multiple notifications.
//        notificationManager.notify(Random.nextInt(), notification)
//    }
//
//    /**
//     * Creates an Intent based on the 'type' field in the data payload.
//     */
//    private fun createIntentForNotification(data: Map<String, String>): Intent {
//        return when (data["type"]) {
//            "new_message" -> Intent(this, chat::class.java).apply {
//                putExtra("userId", data["senderId"])
//                putExtra("username", data["senderName"])
//            }
//            "follow_request" -> Intent(this, home_page::class.java) // Or a dedicated notifications activity
//            "screenshot_alert" -> Intent(this, dms::class.java)
//            else -> Intent(this, home_page::class.java) // Default action
//        }.apply {
//            // These flags ensure that tapping the notification brings the app to the front
//            // or starts it if it's not running.
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//        }
//    }
//
//    /**
//     * Updates the user's FCM token in the Firebase Realtime Database.
//     * Uses the KTX library for cleaner code.
//     */
//    private fun updateTokenInFirebase(token: String) {
//        // Use the KTX 'Firebase.auth' which is the modern way.
//        val userId = Firebase.auth.currentUser?.uid ?: return
//
//        // Use 'Firebase.database' for the database reference.
//        Firebase.database.getReference("users")
//            .child(userId)
//            .child("fcmToken")
//            .setValue(token)
//            .addOnSuccessListener {
//                Log.d(TAG, "FCM token updated successfully in Firebase.")
//            }
//            .addOnFailureListener { e ->
//                Log.e(TAG, "Failed to update FCM token in Firebase.", e)
//            }
//    }
//}
