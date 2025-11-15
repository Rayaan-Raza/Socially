package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

object CallManager {

    /**
     * Step 1: Called by the caller (in ChatActivity).
     * Tells our backend to send an FCM to the other user.
     */
    fun initiateCall(
        context: Context,
        currentUserId: String,
        currentUserName: String,
        otherUserId: String,
        otherUserName: String,
        isVideoCall: Boolean
    ) {
        val queue = Volley.newRequestQueue(context)
        // TODO: Dev A must create this API endpoint
        val url = AppGlobals.BASE_URL + "initiate_call.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        // Server successfully sent FCM to other user.
                        // Now we join the call.
                        val data = json.getJSONObject("data")
                        val channelName = data.getString("channelName")
                        val token = data.getString("agoraToken")

                        Log.d("CallManager", "✅ Call initiated. Joining channel: $channelName")

                        // Start call page for caller
                        val intent = Intent(context, call_page::class.java).apply {
                            putExtra("CHANNEL_NAME", channelName)
                            putExtra("AGORA_TOKEN", token)
                            putExtra("USER_NAME", otherUserName) // Name of person we are calling
                            putExtra("USER_ID", otherUserId)
                            putExtra("IS_VIDEO_CALL", isVideoCall)
                        }
                        context.startActivity(intent)

                    } else {
                        Toast.makeText(context, "Call failed: ${json.getString("message")}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("CallManager", "Error parsing initiate_call: ${e.message}")
                }
            },
            { error ->
                Log.e("CallManager", "Volley error initiating call: ${error.message}")
                Toast.makeText(context, "Network error. Could not start call.", Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["callerId"] = currentUserId
                params["callerName"] = currentUserName
                params["receiverId"] = otherUserId
                params["receiverName"] = otherUserName
                params["isVideoCall"] = isVideoCall.toString()
                return params
            }
        }
        queue.add(stringRequest)
    }

    /**
     * Step 2: Called by the receiver (in IncomingCallActivity)
     * This simply joins the call. No network request needed.
     */
    fun acceptCall(
        context: Context,
        channelName: String,
        token: String,
        callerName: String,
        callerId: String,
        isVideoCall: Boolean
    ) {
        Log.d("CallManager", "Accepting call. Joining channel: $channelName")

        val intent = Intent(context, call_page::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("AGORA_TOKEN", token)
            putExtra("USER_NAME", callerName) // Name of person who is calling us
            putExtra("USER_ID", callerId)
            putExtra("IS_VIDEO_CALL", isVideoCall)
        }
        context.startActivity(intent)
    }

    /**
     * Step 3: Called by receiver to decline, or by anyone to end.
     * Tells the backend to notify the other user that the call is over.
     */
    fun endOrDeclineCall(context: Context, channelName: String, otherUserId: String) {
        Log.d("CallManager", "Ending/Declining call: $channelName")

        val queue = Volley.newRequestQueue(context)
        // TODO: Dev A must create this API endpoint
        val url = AppGlobals.BASE_URL + "end_call.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response -> Log.d("CallManager", "Call end reported to server.") },
            { error -> Log.e("CallManager", "Volley error ending call: ${error.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["channelName"] = channelName
                params["notifyUserId"] = otherUserId // Tell server who to notify
                return params
            }
        }
        queue.add(stringRequest)
    }
}