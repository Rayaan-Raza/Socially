package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

object CallManager {

    private const val TAG = "CallManager"

    // TODO: Update this to your Node.js server URL
    // For testing with ngrok: "https://your-ngrok-url.ngrok.io"
    // For production: Your deployed server URL
    private const val CALL_SERVER_URL = "http://YOUR_NODEJS_SERVER:3000"

    /**
     * Register FCM token with backend
     * Call this after user logs in
     */
    fun registerFcmToken(context: Context, userId: String, onComplete: ((Boolean) -> Unit)? = null) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to get FCM token: ${task.exception}")
                onComplete?.invoke(false)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token obtained: ${token.take(20)}...")

            // Save locally
            val prefs = context.getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()

            // Send to server
            val queue = Volley.newRequestQueue(context)
            val url = AppGlobals.BASE_URL + "user_update_fcm.php"

            val request = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d(TAG, "✅ FCM token registered on server")
                            onComplete?.invoke(true)
                        } else {
                            Log.e(TAG, "❌ Failed: ${json.optString("message")}")
                            onComplete?.invoke(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error: ${e.message}")
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
    }

    /**
     * Initiate a call to another user
     *
     * Flow:
     * 1. Get receiver's FCM token from PHP backend
     * 2. Send call notification via Node.js server
     * 3. Open outgoing call screen
     */
    fun initiateCall(
        context: Context,
        currentUserId: String,
        currentUserName: String,
        otherUserId: String,
        otherUserName: String,
        isVideoCall: Boolean
    ) {
        Log.d(TAG, "📞 Initiating ${if (isVideoCall) "video" else "audio"} call to $otherUserName")

        val queue = Volley.newRequestQueue(context)

        // Step 1: Get receiver's FCM token
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$otherUserId"

        val fcmRequest = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val receiverFcmToken = dataObj.getString("fcmToken")

                        Log.d(TAG, "Got receiver's FCM token")

                        // Step 2: Send call notification via Node.js
                        sendCallNotificationViaNodeJS(
                            context,
                            currentUserId,
                            currentUserName,
                            otherUserId,
                            otherUserName,
                            receiverFcmToken,
                            isVideoCall
                        )
                    } else {
                        val errorMsg = json.optString("message", "User not available")
                        Log.e(TAG, "❌ $errorMsg")
                        Toast.makeText(context, "Cannot call: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing FCM token response: ${e.message}")
                    Toast.makeText(context, "Failed to initiate call", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                Log.e(TAG, "Network error getting FCM token: ${error.message}")
                Toast.makeText(context, "User is not available for calls", Toast.LENGTH_LONG).show()
            }
        )

        queue.add(fcmRequest)
    }

    private fun sendCallNotificationViaNodeJS(
        context: Context,
        callerUid: String,
        callerName: String,
        receiverUid: String,
        receiverName: String,
        receiverFcmToken: String,
        isVideoCall: Boolean
    ) {
        val queue = Volley.newRequestQueue(context)
        val url = "$CALL_SERVER_URL/call/initiate"

        // Generate channel name for Agora
        val channelName = if (callerUid < receiverUid) {
            "${callerUid}_${receiverUid}"
        } else {
            "${receiverUid}_${callerUid}"
        }

        val payload = JSONObject().apply {
            put("callerUid", callerUid)
            put("callerName", callerName)
            put("receiverFcmToken", receiverFcmToken)
            put("callType", if (isVideoCall) "video" else "audio")
            put("channelName", channelName)
        }

        Log.d(TAG, "Sending call notification to Node.js server...")

        val request = JsonObjectRequest(Request.Method.POST, url, payload,
            { response ->
                try {
                    if (response.getBoolean("success")) {
                        val dataObj = response.getJSONObject("data")
                        val callId = dataObj.getString("callId")

                        Log.d(TAG, "✅ Call initiated. Call ID: $callId, Channel: $channelName")

                        // Save call state
                        saveCallState(context, callId, channelName, receiverFcmToken, receiverUid, isVideoCall)

                        // Open outgoing call screen
                        val intent = Intent(context, call_page::class.java).apply {
                            putExtra("CALL_ID", callId)
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("USER_NAME", receiverName)
                            putExtra("USER_ID", receiverUid)
                            putExtra("IS_VIDEO_CALL", isVideoCall)
                            putExtra("IS_OUTGOING", true)
                        }
                        context.startActivity(intent)

                    } else {
                        val errorMsg = response.optString("message", "Failed to initiate call")
                        Log.e(TAG, "❌ $errorMsg")
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing call response: ${e.message}")
                    Toast.makeText(context, "Failed to initiate call", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                Log.e(TAG, "Network error: ${error.message}")
                Toast.makeText(context, "Call server unavailable", Toast.LENGTH_LONG).show()
            }
        )

        queue.add(request)
    }

    /**
     * Accept an incoming call
     * Called by IncomingCall activity
     */
    fun acceptCall(
        context: Context,
        callId: String,
        channelName: String,
        callerName: String,
        callerUid: String,
        isVideoCall: Boolean
    ) {
        Log.d(TAG, "✅ Accepting call $callId")

        // Notify caller that call was accepted
        notifyCallAnswered(context, callId, callerUid)

        // Open call screen
        val intent = Intent(context, call_page::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("USER_NAME", callerName)
            putExtra("USER_ID", callerUid)
            putExtra("IS_VIDEO_CALL", isVideoCall)
            putExtra("IS_OUTGOING", false)
        }
        context.startActivity(intent)
    }

    /**
     * Decline an incoming call
     */
    fun declineCall(context: Context, callId: String, callerUid: String) {
        Log.d(TAG, "❌ Declining call $callId")
        notifyCallDeclined(context, callId, callerUid)
    }

    /**
     * End an ongoing call
     */
    fun endCall(context: Context, callId: String) {
        Log.d(TAG, "📴 Ending call $callId")

        val callState = getCurrentCallState(context) ?: return
        val otherUserFcmToken = callState["otherFcmToken"] as? String ?: ""
        val otherUserId = callState["otherUserId"] as? String ?: ""

        if (otherUserFcmToken.isNotEmpty()) {
            sendCallEndNotification(context, callId, otherUserFcmToken, "ended")
        } else if (otherUserId.isNotEmpty()) {
            // Fetch token and then send
            fetchTokenAndSendEnd(context, callId, otherUserId, "ended")
        }

        clearCallState(context)
    }

    private fun notifyCallAnswered(context: Context, callId: String, callerUid: String) {
        // Get caller's FCM token and notify them
        val queue = Volley.newRequestQueue(context)
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$callerUid"

        val request = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val token = json.getJSONObject("data").getString("fcmToken")
                        sendCallAnsweredNotification(context, callId, token)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}")
                }
            },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun notifyCallDeclined(context: Context, callId: String, callerUid: String) {
        val queue = Volley.newRequestQueue(context)
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$callerUid"

        val request = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val token = json.getJSONObject("data").getString("fcmToken")
                        sendCallEndNotification(context, callId, token, "declined")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}")
                }
            },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun fetchTokenAndSendEnd(context: Context, callId: String, userId: String, reason: String) {
        val queue = Volley.newRequestQueue(context)
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$userId"

        val request = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val token = json.getJSONObject("data").getString("fcmToken")
                        sendCallEndNotification(context, callId, token, reason)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ${e.message}")
                }
            },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun sendCallAnsweredNotification(context: Context, callId: String, callerFcmToken: String) {
        val queue = Volley.newRequestQueue(context)
        val url = "$CALL_SERVER_URL/call/answered"

        val payload = JSONObject().apply {
            put("callerFcmToken", callerFcmToken)
            put("callId", callId)
        }

        val request = JsonObjectRequest(Request.Method.POST, url, payload,
            { Log.d(TAG, "Call answered notification sent") },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun sendCallEndNotification(context: Context, callId: String, fcmToken: String, reason: String) {
        val queue = Volley.newRequestQueue(context)
        val url = "$CALL_SERVER_URL/call/end"

        val payload = JSONObject().apply {
            put("receiverFcmToken", fcmToken)
            put("callId", callId)
            put("reason", reason)
        }

        val request = JsonObjectRequest(Request.Method.POST, url, payload,
            { Log.d(TAG, "Call end notification sent") },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    // ========== CALL STATE MANAGEMENT ==========

    private fun saveCallState(
        context: Context,
        callId: String,
        channelName: String,
        otherFcmToken: String,
        otherUserId: String,
        isVideoCall: Boolean
    ) {
        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("call_id", callId)
            .putString("channel_name", channelName)
            .putString("other_fcm_token", otherFcmToken)
            .putString("other_user_id", otherUserId)
            .putBoolean("is_video_call", isVideoCall)
            .putLong("call_start_time", System.currentTimeMillis())
            .apply()
    }

    fun getCurrentCallState(context: Context): Map<String, Any>? {
        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        val callId = prefs.getString("call_id", null) ?: return null

        return mapOf(
            "callId" to callId,
            "channelName" to (prefs.getString("channel_name", "") ?: ""),
            "otherFcmToken" to (prefs.getString("other_fcm_token", "") ?: ""),
            "otherUserId" to (prefs.getString("other_user_id", "") ?: ""),
            "isVideoCall" to prefs.getBoolean("is_video_call", false),
            "callStartTime" to prefs.getLong("call_start_time", 0L)
        )
    }

    fun clearCallState(context: Context) {
        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}