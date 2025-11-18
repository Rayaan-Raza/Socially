//package com.group.i230535_i230048
//
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Intent
//import android.os.Build
//import android.os.IBinder
//import androidx.core.app.NotificationCompat
//import com.google.firebase.auth.FirebaseAuth
//
//class CallListenerService : Service() {
//
//    companion object {
//        private const val CHANNEL_ID = "CallServiceChannel"
//        private const val NOTIFICATION_ID = 1
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//        startForeground(NOTIFICATION_ID, createNotification())
//        startListeningForCalls()
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                "Call Service",
//                NotificationManager.IMPORTANCE_LOW
//            ).apply {
//                description = "Listening for incoming calls"
//            }
//
//            val manager = getSystemService(NotificationManager::class.java)
//            manager.createNotificationChannel(channel)
//        }
//    }
//
//    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
//        .setContentTitle("Call Service")
//        .setContentText("Ready to receive calls")
//        .setSmallIcon(android.R.drawable.ic_menu_call) // Use system icon as fallback
//        .setPriority(NotificationCompat.PRIORITY_LOW)
//        .build()
//
//    private fun startListeningForCalls() {
//        val currentUser = FirebaseAuth.getInstance().currentUser
//        currentUser?.let { user ->
//            CallManager.listenForIncomingCalls(user.uid) { callId, callerName, isVideoCall ->
//                // Show incoming call activity
//                val intent = Intent(this, IncomingCallActivity::class.java).apply {
//                    putExtra("CALL_ID", callId)
//                    putExtra("CALLER_NAME", callerName)
//                    putExtra("IS_VIDEO_CALL", isVideoCall)
//                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//                }
//                startActivity(intent)
//            }
//        }
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopForeground(true)
//    }
//}
//
//// Start this service from your MainActivity:
//// Intent(this, CallListenerService::class.java).also {
////     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////         startForegroundService(it)
////     } else {
////         startService(it)
////     }
//// }