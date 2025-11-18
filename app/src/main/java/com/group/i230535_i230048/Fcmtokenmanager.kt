package com.group.i230535_i230048

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

/**
 * Manages FCM token generation and registration with backend
 */
object FCMTokenManager {
    private const val TAG = "FCMTokenManager"

    /**
     * Get the current FCM token and send it to the server
     */
    fun registerToken(context: Context, userId: String, onComplete: ((Boolean) -> Unit)? = null) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to get FCM token: ${task.exception}")
                onComplete?.invoke(false)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token: $token")

            // Save locally
            val prefs = context.getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()

            // Send to server
            sendTokenToServer(context, userId, token, onComplete)
        }
    }

    private fun sendTokenToServer(
        context: Context,
        userId: String,
        token: String,
        onComplete: ((Boolean) -> Unit)?
    ) {
        val queue = Volley.newRequestQueue(context)
        val url = AppGlobals.BASE_URL + "user_update_fcm.php"

        Log.d(TAG, "Sending FCM token to server for user: $userId")

        val request = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        Log.d(TAG, "✅ FCM token registered successfully")
                        onComplete?.invoke(true)
                    } else {
                        Log.e(TAG, "❌ Failed to register token: ${json.optString("message")}")
                        onComplete?.invoke(false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response: ${e.message}")
                    onComplete?.invoke(false)
                }
            },
            { error ->
                Log.e(TAG, "Network error: ${error.message}")
                onComplete?.invoke(false)
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

    /**
     * Remove FCM token from server (on logout)
     */
    fun unregisterToken(context: Context, userId: String) {
        val queue = Volley.newRequestQueue(context)
        val url = AppGlobals.BASE_URL + "user_update_fcm.php"

        val request = object : StringRequest(Request.Method.POST, url,
            { response -> Log.d(TAG, "Token unregistered") },
            { error -> Log.e(TAG, "Error unregistering token: ${error.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "uid" to userId,
                    "fcmToken" to "" // Empty token = remove
                )
            }
        }

        queue.add(request)
    }
}