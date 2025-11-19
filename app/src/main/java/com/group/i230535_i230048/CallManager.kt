package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

object CallManager {

    private const val TAG = "CallManager"

    // ✅ EMULATOR FIX: Use 10.0.2.2 to reach your Laptop's localhost
    private const val CALL_SERVER_URL = "http://192.168.100.150:3000"

    /**
     * Register FCM token with backend (PHP)
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

            // Send to PHP server
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
                            Log.e(TAG, "❌ PHP Failed: ${json.optString("message")}")
                            onComplete?.invoke(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing PHP response: ${e.message}")
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
     * Initiate a call
     */
    fun initiateCall(
        context: Context,
        currentUserId: String,
        currentUserName: String,
        otherUserId: String,
        otherUserName: String,
        isVideoCall: Boolean
    ) {
        // 1. Construct the URL
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$otherUserId"

        // 🔍 DEBUG LOG: Verify this is your ACTUAL live URL
        Log.e(TAG, "🔍 DEBUG: Attempting to connect to: $fcmUrl")

        val queue = Volley.newRequestQueue(context)
        val fcmRequest = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                Log.d(TAG, "✅ PHP Connection Successful!")
                // ... (Your existing parsing logic here) ...

                try {
                    val cleanedResponse = response.trim().substringAfter("{").substringBeforeLast("}")
                    val json = JSONObject("{$cleanedResponse}")

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val receiverFcmToken = dataObj.getString("fcmToken")

                        // Generate Channel Name
                        val channelName = if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
                        val tempCallId = "call_${System.currentTimeMillis()}"

                        // ✅ OPEN UI NOW
                        openCallPage(context, tempCallId, channelName, otherUserName, otherUserId, isVideoCall, true)

                        // Send to Node
                        sendCallNotificationViaNodeJS(context, currentUserId, currentUserName, otherUserId, otherUserName, receiverFcmToken, isVideoCall, channelName)
                    } else {
                        Toast.makeText(context, "User not available (No Token)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ JSON Parse Error: ${e.message}")
                }
            },
            { error ->
                // 🔍 DIAGNOSTIC LOGGING
                Log.e(TAG, "❌ PHP FAILURE REASON: ${error.message}")
                if (error.networkResponse != null) {
                    Log.e(TAG, "❌ Status Code: ${error.networkResponse.statusCode}")
                } else {
                    Log.e(TAG, "❌ No Network Response. Likely DNS, SSL, or Offline.")
                    error.printStackTrace() // This prints the full technical error to Logcat
                }

                Toast.makeText(context, "Connection Failed. Check Logcat.", Toast.LENGTH_LONG).show()
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
        isVideoCall: Boolean,
        channelName: String
    ) {
        val queue = Volley.newRequestQueue(context)
        val url = "$CALL_SERVER_URL/call/initiate"

        val payload = JSONObject().apply {
            put("callerUid", callerUid)
            put("callerName", callerName)
            put("receiverFcmToken", receiverFcmToken)
            put("callType", if (isVideoCall) "video" else "audio")
            put("channelName", channelName)
        }

        Log.d(TAG, "Sending Payload to Node: $payload")

        val request = object : JsonObjectRequest(Request.Method.POST, url, payload,
            { response ->
                Log.d(TAG, "✅ Node Success: $response")
                try {
                    if (response.getBoolean("success")) {
                        val dataObj = response.getJSONObject("data")
                        val finalCallId = dataObj.getString("callId")
                        // Update state with real Call ID
                        saveCallState(context, finalCallId, channelName, receiverFcmToken, receiverUid, isVideoCall)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Node response: ${e.message}")
                }
            },
            { error ->
                // DETAILED ERROR LOGGING
                Log.e(TAG, "❌ Node.js Failure Details:")
                Log.e(TAG, "   Message: ${error.message}")
                if (error.networkResponse != null) {
                    Log.e(TAG, "   Status Code: ${error.networkResponse.statusCode}")
                    try {
                        val body = String(error.networkResponse.data, Charsets.UTF_8)
                        Log.e(TAG, "   Server Body: $body")
                    } catch (e: Exception) {}
                } else {
                    Log.e(TAG, "   No Network Response (Likely Firewall, IP, or Node not running)")
                }

                // We don't close the page here, we let the user manually end it if it hangs,
                // or you can broadcast an Intent to close it.
            }
        ) {
            // Increase timeout for Emulator/Laptop connection
            override fun getRetryPolicy() = DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
        queue.add(request)
    }

    private fun openCallPage(context: Context, callId: String, channelName: String, userName: String, userId: String, isVideoCall: Boolean, isOutgoing: Boolean) {
        val intent = Intent(context, call_page::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CHANNEL_NAME", channelName)
            putExtra("USER_NAME", userName)
            putExtra("USER_ID", userId)
            putExtra("IS_VIDEO_CALL", isVideoCall)
            putExtra("IS_OUTGOING", isOutgoing)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Accept an incoming call
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

        notifyCallAnswered(context, callId, callerUid)

        openCallPage(context, callId, channelName, callerName, callerUid, isVideoCall, false)
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
            // Fetch token and then send (Edge case)
            fetchTokenAndSendEnd(context, callId, otherUserId, "ended")
        }

        clearCallState(context)
    }

    // ========== NOTIFICATION HELPERS ==========

    private fun notifyCallAnswered(context: Context, callId: String, callerUid: String) {
        val queue = Volley.newRequestQueue(context)
        val fcmUrl = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$callerUid"

        val request = StringRequest(Request.Method.GET, fcmUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val token = json.getJSONObject("data").getString("fcmToken")
                        sendNodeNotification(context, "answered", token, callId)
                    }
                } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
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
                        sendNodeNotification(context, "declined", token, callId)
                    }
                } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
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
                } catch (e: Exception) { Log.e(TAG, "Error: ${e.message}") }
            },
            { error -> Log.e(TAG, "Error: ${error.message}") }
        )
        queue.add(request)
    }

    private fun sendNodeNotification(context: Context, type: String, token: String, callId: String) {
        val queue = Volley.newRequestQueue(context)
        val url = "$CALL_SERVER_URL/call/$type" // answered or declined

        val payload = JSONObject()
        if (type == "answered") payload.put("callerFcmToken", token) else payload.put("callerFcmToken", token)
        payload.put("callId", callId)

        val request = JsonObjectRequest(Request.Method.POST, url, payload, {}, { error -> Log.e(TAG, "Node Error: ${error.message}")})
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

        val request = JsonObjectRequest(Request.Method.POST, url, payload, {}, { error -> Log.e(TAG, "Node Error: ${error.message}")})
        queue.add(request)
    }

    // ========== STATE MANAGEMENT ==========

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