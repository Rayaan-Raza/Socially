package com.group.i230535_i230048

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class IncomingCall : AppCompatActivity() {

    companion object {
        private const val TAG = "IncomingCall"
        private const val CALL_TIMEOUT_MS = 30000L // 30 seconds
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var callId: String = ""
    private var channelName: String = ""
    private var callerUid: String = ""
    private var callerName: String = "Unknown"
    private var isVideoCall: Boolean = false

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Broadcast receiver for call ended events
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val endedCallId = intent?.getStringExtra("callId") ?: return
            val reason = intent.getStringExtra("reason") ?: "cancelled"

            if (endedCallId == callId) {
                Log.d(TAG, "Call was cancelled by caller. Reason: $reason")
                stopRinging()

                val message = when (reason) {
                    "cancelled" -> "Call cancelled"
                    "timeout" -> "Call timed out"
                    else -> "Call ended"
                }
                Toast.makeText(this@IncomingCall, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Disable back button - user must explicitly accept or decline
        onBackPressedDispatcher.addCallback(this) {
            // Do nothing - prevent back press
        }

        // Make sure activity shows over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_incoming_call)

        // Get call data from intent
        callId = intent.getStringExtra("CALL_ID") ?: ""
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: callId
        callerUid = intent.getStringExtra("CALLER_UID") ?: ""
        callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        Log.d(TAG, "📞 Incoming ${if (isVideoCall) "video" else "audio"} call")
        Log.d(TAG, "Call ID: $callId")
        Log.d(TAG, "Channel: $channelName")
        Log.d(TAG, "Caller: $callerName ($callerUid)")

        // Handle notification actions
        when (intent.action) {
            "ACCEPT_CALL" -> {
                Log.d(TAG, "Accept action from notification")
                acceptCall()
                return
            }
            "DECLINE_CALL" -> {
                Log.d(TAG, "Decline action from notification")
                declineCall()
                return
            }
        }

        if (callId.isEmpty() || callerUid.isEmpty()) {
            Log.e(TAG, "Invalid call data")
            Toast.makeText(this, "Invalid call", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        startRinging()
        startCallTimeout()
        registerCallEndedReceiver()
    }

    private fun setupUI() {
        // Set caller name
        findViewById<TextView>(R.id.caller_name)?.text = callerName

        // Set call type text
        val callTypeText = if (isVideoCall) "Incoming video call..." else "Incoming voice call..."
        findViewById<TextView>(R.id.call_type)?.text = callTypeText

        // Set avatar initials
        val initials = if (callerName.isNotEmpty()) {
            callerName.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.toString()?.uppercase() }
                .joinToString("")
        } else "?"
        findViewById<TextView>(R.id.caller_avatar_text)?.text = initials

        // Accept call button
        findViewById<ImageView>(R.id.accept_call)?.setOnClickListener {
            acceptCall()
        }

        // Decline call button
        findViewById<ImageView>(R.id.decline_call)?.setOnClickListener {
            declineCall()
        }
    }

    private fun acceptCall() {
        Log.d(TAG, "✅ Accepting call $callId")
        stopRinging()
        cancelTimeout()
        cancelNotification()
        unregisterCallEndedReceiver()

        // Use CallManager to accept
        CallManager.acceptCall(
            this,
            callId,
            channelName,
            callerName,
            callerUid,
            isVideoCall
        )

        finish()
    }

    private fun declineCall() {
        Log.d(TAG, "❌ Declining call $callId")
        stopRinging()
        cancelTimeout()
        cancelNotification()
        unregisterCallEndedReceiver()

        // Notify caller that call was declined
        CallManager.declineCall(this, callId, callerUid)

        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startRinging() {
        // Play ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }

            ringtone?.play()
            Log.d(TAG, "🔔 Ringtone started")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing ringtone: ${e.message}")
        }

        // Vibrate
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 500, 1000, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d(TAG, "📳 Vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}")
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
            ringtone = null
            Log.d(TAG, "🔕 Ringtone stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }

        try {
            vibrator?.cancel()
            vibrator = null
            Log.d(TAG, "📴 Vibration stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration: ${e.message}")
        }
    }

    private fun startCallTimeout() {
        timeoutRunnable = Runnable {
            Log.d(TAG, "⏰ Call timeout - no answer")
            stopRinging()

            // Notify caller that call timed out (treated as decline)
            CallManager.declineCall(this, callId, callerUid)

            Toast.makeText(this, "Missed call from $callerName", Toast.LENGTH_LONG).show()
            finish()
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, CALL_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let {
            timeoutHandler.removeCallbacks(it)
        }
        timeoutRunnable = null
    }

    private fun cancelNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(callId.hashCode())
    }

    private fun registerCallEndedReceiver() {
        val filter = IntentFilter("com.group.i230535_i230048.CALL_ENDED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                callEndedReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
//            registerReceiver(callEndedReceiver, filter)
        }
    }

    private fun unregisterCallEndedReceiver() {
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
        cancelTimeout()
        unregisterCallEndedReceiver()
    }
}