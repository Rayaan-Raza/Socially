package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object CallManager {

    private val database = FirebaseDatabase.getInstance()

    /**
     * Initiate a call to another user
     */
    fun initiateCall(
        context: Context,
        currentUserId: String,
        currentUserName: String,
        otherUserId: String,
        otherUserName: String,
        isVideoCall: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Generate unique channel name (same for both users)
        val channelName = generateChannelName(currentUserId, otherUserId)

        // Create call data
        val callData = hashMapOf(
            "callId" to channelName,
            "callerId" to currentUserId,
            "callerName" to currentUserName,
            "receiverId" to otherUserId,
            "receiverName" to otherUserName,
            "channelName" to channelName,
            "isVideoCall" to isVideoCall,
            "callStatus" to "calling", // calling, accepted, declined, ended
            "timestamp" to System.currentTimeMillis()
        )

        android.util.Log.d("CallManager", "Initiating call: $channelName")
        android.util.Log.d("CallManager", "Caller: $currentUserId -> Receiver: $otherUserId")

        // Save to Firebase
        database.getReference("calls").child(channelName).setValue(callData)
            .addOnSuccessListener {
                android.util.Log.d("CallManager", "✅ Call data saved to Firebase")

                // Start call page for caller
                val intent = Intent(context, call_page::class.java).apply {
                    putExtra("CHANNEL_NAME", channelName)
                    putExtra("USER_NAME", otherUserName)
                    putExtra("USER_ID", otherUserId)
                    putExtra("IS_VIDEO_CALL", isVideoCall)
                    putExtra("IS_CALLER", true)
                }
                context.startActivity(intent)
                onSuccess()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("CallManager", "❌ Failed to save call: ${e.message}")
                onError(e.message ?: "Failed to initiate call")
            }
    }

    /**
     * Listen for incoming calls for a specific user
     */
    fun listenForIncomingCalls(
        userId: String,
        onIncomingCall: (callId: String, callerName: String, isVideoCall: Boolean) -> Unit
    ) {
        val callsRef = database.getReference("calls")

        android.util.Log.d("CallManager", "👂 Listening for calls for user: $userId")

        callsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (callSnapshot in snapshot.children) {
                    val receiverId = callSnapshot.child("receiverId").getValue(String::class.java)
                    val callStatus = callSnapshot.child("callStatus").getValue(String::class.java)

                    // Check if this call is for the current user and is still ringing
                    if (receiverId == userId && callStatus == "calling") {
                        val callId = callSnapshot.child("callId").getValue(String::class.java) ?: ""
                        val callerName = callSnapshot.child("callerName").getValue(String::class.java) ?: "Unknown"
                        val isVideoCall = callSnapshot.child("isVideoCall").getValue(Boolean::class.java) ?: false

                        android.util.Log.d("CallManager", "📞 Incoming call from: $callerName")
                        onIncomingCall(callId, callerName, isVideoCall)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CallManager", "Error listening for calls: ${error.message}")
            }
        })
    }

    /**
     * Accept an incoming call
     */
    fun acceptCall(
        context: Context,
        callId: String,
        onSuccess: () -> Unit = {}
    ) {
        val callRef = database.getReference("calls").child(callId)

        android.util.Log.d("CallManager", "Accepting call: $callId")

        // Update call status to accepted
        callRef.child("callStatus").setValue("accepted")
            .addOnSuccessListener {
                android.util.Log.d("CallManager", "✅ Call status updated to accepted")

                // Get call details and join
                callRef.get().addOnSuccessListener { snapshot ->
                    val callerName = snapshot.child("callerName").getValue(String::class.java) ?: "Unknown"
                    val callerId = snapshot.child("callerId").getValue(String::class.java) ?: ""
                    val channelName = snapshot.child("channelName").getValue(String::class.java) ?: callId
                    val isVideoCall = snapshot.child("isVideoCall").getValue(Boolean::class.java) ?: false

                    android.util.Log.d("CallManager", "Joining channel: $channelName")

                    // Start call page for receiver
                    val intent = Intent(context, call_page::class.java).apply {
                        putExtra("CHANNEL_NAME", channelName)
                        putExtra("USER_NAME", callerName)
                        putExtra("USER_ID", callerId)
                        putExtra("IS_VIDEO_CALL", isVideoCall)
                        putExtra("IS_CALLER", false)
                    }
                    context.startActivity(intent)
                    onSuccess()
                }
            }
    }

    /**
     * Decline an incoming call
     */
    fun declineCall(callId: String) {
        android.util.Log.d("CallManager", "Declining call: $callId")
        database.getReference("calls").child(callId).child("callStatus").setValue("declined")
    }

    /**
     * End a call
     */
    fun endCall(callId: String) {
        android.util.Log.d("CallManager", "Ending call: $callId")
        database.getReference("calls").child(callId).removeValue()
    }

    /**
     * Generate a unique channel name for two users
     * Same result regardless of who calls whom
     */
    private fun generateChannelName(userId1: String, userId2: String): String {
        val sortedIds = listOf(userId1, userId2).sorted()
        return "call_${sortedIds[0]}_${sortedIds[1]}"
    }
}

// Data class for call information
data class CallInfo(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val channelName: String = "",
    val isVideoCall: Boolean = false,
    val callStatus: String = "calling",
    val timestamp: Long = 0L
)