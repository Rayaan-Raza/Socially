package com.group.i230535_i230048

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

object NotificationHelper {
    private const val TAG = "NotifyHelper"

    // ✅ Make sure this matches your CallManager URL
    private const val NODE_SERVER_URL = "http://10.0.2.2:3000"

    fun sendNewMessageNotification(
        context: Context,
        receiverUid: String,
        senderName: String,
        content: String,
        chatId: String,
        messageType: String
    ) {
        // 1. Get Receiver Token
        getFcmToken(context, receiverUid) { token ->
            if (token.isNotEmpty()) {
                // 2. Send to Node.js
                val url = "$NODE_SERVER_URL/message/send"
                val payload = JSONObject().apply {
                    put("receiverFcmToken", token)
                    put("senderName", senderName)
                    put("content", content)
                    put("chatId", chatId)
                    put("messageType", messageType)
                }
                sendToNode(context, url, payload)
            }
        }
    }

    fun sendScreenshotNotification(
        context: Context,
        receiverUid: String,
        takerName: String,
        chatId: String
    ) {
        // 1. Get Receiver Token
        getFcmToken(context, receiverUid) { token ->
            if (token.isNotEmpty()) {
                // 2. Send to Node.js
                val url = "$NODE_SERVER_URL/screenshot/notify"
                val payload = JSONObject().apply {
                    put("receiverFcmToken", token)
                    put("takerName", takerName)
                    put("chatId", chatId)
                }
                sendToNode(context, url, payload)
            }
        }
    }

    private fun getFcmToken(context: Context, uid: String, callback: (String) -> Unit) {
        val queue = Volley.newRequestQueue(context)
        val url = AppGlobals.BASE_URL + "user_fcm_get.php?uid=$uid"

        val req = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleaned = response.trim().substringAfter("{").substringBeforeLast("}")
                    val json = JSONObject("{$cleaned}")
                    if (json.getBoolean("success")) {
                        callback(json.getJSONObject("data").getString("fcmToken"))
                    } else {
                        Log.e(TAG, "User has no token")
                        callback("")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    callback("")
                }
            },
            { Log.e(TAG, "Network error fetching token") }
        )
        queue.add(req)
    }

    private fun sendToNode(context: Context, url: String, payload: JSONObject) {
        val queue = Volley.newRequestQueue(context)
        val req = JsonObjectRequest(Request.Method.POST, url, payload,
            { Log.d(TAG, "✅ Notification sent: $url") },
            { error -> Log.e(TAG, "❌ Notification failed: ${error.message}") }
        )
        queue.add(req)
    }
}