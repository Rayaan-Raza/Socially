package com.rayaanraza.i230535

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"
    // TODO: Replace with your Firebase Server Key from Firebase Console > Project Settings > Cloud Messaging
    private const val SERVER_KEY = "YOUR_FIREBASE_SERVER_KEY_HERE"

    /**
     * Send a message notification
     */
    fun sendMessageNotification(
        receiverId: String,
        senderName: String,
        messageText: String,
        chatId: String,
        senderId: String
    ) {
        Log.d(TAG, "Sending message notification to: $receiverId")

        // Get receiver's FCM token
        getFcmToken(receiverId) { token ->
            if (token != null) {
                val data = mapOf(
                    "type" to "message",
                    "senderName" to senderName,
                    "messageText" to messageText,
                    "chatId" to chatId,
                    "senderId" to senderId
                )

                val notification = mapOf(
                    "title" to senderName,
                    "body" to messageText
                )

                sendFcmNotification(token, data, notification)
            } else {
                Log.e(TAG, "No FCM token found for user: $receiverId")
            }
        }
    }

    /**
     * Send a call notification
     */
    fun sendCallNotification(
        receiverId: String,
        callerName: String,
        callId: String,
        isVideoCall: Boolean
    ) {
        Log.d(TAG, "Sending call notification to: $receiverId")

        getFcmToken(receiverId) { token ->
            if (token != null) {
                val callType = if (isVideoCall) "video" else "voice"

                val data = mapOf(
                    "type" to "call",
                    "callerName" to callerName,
                    "callId" to callId,
                    "isVideoCall" to isVideoCall.toString()
                )

                val notification = mapOf(
                    "title" to "Incoming $callType call",
                    "body" to "$callerName is calling..."
                )

                sendFcmNotification(token, data, notification)
            }
        }
    }

    /**
     * Send a like notification
     */
    fun sendLikeNotification(
        receiverId: String,
        userName: String,
        postId: String
    ) {
        Log.d(TAG, "Sending like notification to: $receiverId")

        getFcmToken(receiverId) { token ->
            if (token != null) {
                val data = mapOf(
                    "type" to "like",
                    "userName" to userName,
                    "postId" to postId
                )

                val notification = mapOf(
                    "title" to "New Like",
                    "body" to "$userName liked your post"
                )

                sendFcmNotification(token, data, notification)
            }
        }
    }

    /**
     * Send a comment notification
     */
    fun sendCommentNotification(
        receiverId: String,
        userName: String,
        commentText: String,
        postId: String
    ) {
        Log.d(TAG, "Sending comment notification to: $receiverId")

        getFcmToken(receiverId) { token ->
            if (token != null) {
                val data = mapOf(
                    "type" to "comment",
                    "userName" to userName,
                    "commentText" to commentText,
                    "postId" to postId
                )

                val notification = mapOf(
                    "title" to "New Comment",
                    "body" to "$userName: $commentText"
                )

                sendFcmNotification(token, data, notification)
            }
        }
    }

    /**
     * Send a follow notification
     */
    fun sendFollowNotification(
        receiverId: String,
        userName: String,
        userId: String
    ) {
        Log.d(TAG, "Sending follow notification to: $receiverId")

        getFcmToken(receiverId) { token ->
            if (token != null) {
                val data = mapOf(
                    "type" to "follow",
                    "userName" to userName,
                    "userId" to userId
                )

                val notification = mapOf(
                    "title" to "New Follower",
                    "body" to "$userName started following you"
                )

                sendFcmNotification(token, data, notification)
            }
        }
    }

    /**
     * Get FCM token from Firebase
     */
    private fun getFcmToken(userId: String, callback: (String?) -> Unit) {
        FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("fcmToken")
            .get()
            .addOnSuccessListener { snapshot ->
                val token = snapshot.getValue(String::class.java)
                callback(token)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token: ${e.message}")
                callback(null)
            }
    }

    /**
     * Send FCM notification via HTTP request
     */
    private fun sendFcmNotification(
        token: String,
        data: Map<String, String>,
        notification: Map<String, String>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(FCM_URL)
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "key=$SERVER_KEY")
                connection.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("to", token)
                    put("priority", "high")

                    // Data payload
                    val dataObj = JSONObject()
                    data.forEach { (key, value) ->
                        dataObj.put(key, value)
                    }
                    put("data", dataObj)

                    // Notification payload
                    val notificationObj = JSONObject()
                    notification.forEach { (key, value) ->
                        notificationObj.put(key, value)
                    }
                    put("notification", notificationObj)
                }

                Log.d(TAG, "Sending FCM request: $jsonBody")

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                Log.d(TAG, "FCM response code: $responseCode")

                if (responseCode == 200) {
                    Log.d(TAG, "✅ Notification sent successfully")
                } else {
                    Log.e(TAG, "❌ Failed to send notification: $responseCode")
                }

                connection.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending notification: ${e.message}", e)
            }
        }
    }
}