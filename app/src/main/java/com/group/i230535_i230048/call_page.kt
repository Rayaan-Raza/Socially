package com.group.i230535_i230048

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class call_page : AppCompatActivity() {

    companion object {
        private const val TAG = "CallPage"
        private const val PERMISSION_REQ_ID = 22
        private val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    private lateinit var muteBtn: ImageView
    private lateinit var endCallBtn: ImageView
    private lateinit var actionBtn: ImageView
    private lateinit var timerText: TextView
    private lateinit var nameText: TextView
    private lateinit var avatarText: TextView

    private var muted = false
    private var speakerOn = false

    private var callId: String = ""
    private var channelName: String = ""
    private var otherUserName: String = ""
    private var otherUserId: String = ""
    private var isVideoCall: Boolean = false
    private var isOutgoing: Boolean = false

    // Agora
    private val APP_ID = "e9d3c619be27400fb63d6293be8bf820" // Your Agora App ID
    private var rtcEngine: RtcEngine? = null

    // Call timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callStartTime: Long = 0
    private var timerRunnable: Runnable? = null
    private var isCallConnected = false

    // Broadcast receivers
    private val callAnsweredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val answeredCallId = intent?.getStringExtra("callId") ?: return
            if (answeredCallId == callId) {
                Log.d(TAG, "✅ Call was answered!")
                runOnUiThread {
                    timerText.text = "Connecting..."
                }
            }
        }
    }

    private val callDeclinedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val declinedCallId = intent?.getStringExtra("callId") ?: return
            if (declinedCallId == callId) {
                Log.d(TAG, "❌ Call was declined")
                runOnUiThread {
                    timerText.text = "Call declined"
                    Toast.makeText(this@call_page, "Call declined", Toast.LENGTH_SHORT).show()
                }
                timerText.postDelayed({ endCall() }, 1500)
            }
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val endedCallId = intent?.getStringExtra("callId") ?: return
            if (endedCallId == callId) {
                Log.d(TAG, "📴 Call ended by other party")
                runOnUiThread {
                    timerText.text = "Call ended"
                }
                timerText.postDelayed({ endCall() }, 1500)
            }
        }
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                Log.d(TAG, "✅ Joined channel: $channel, uid: $uid")
                if (isOutgoing) {
                    timerText.text = "Ringing..."
                } else {
                    timerText.text = "Connected"
                    startCallTimer()
                }
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                Log.d(TAG, "👤 Remote user joined: $uid")
                timerText.text = "Connected"
                if (!isCallConnected) {
                    isCallConnected = true
                    startCallTimer()
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Log.d(TAG, "❌ Remote user left: $uid")
                timerText.text = "Call ended"
                timerText.postDelayed({ endCall() }, 1500)
            }
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            runOnUiThread {
                Log.d(TAG, "🔴 Left channel")
                timerText.text = "Call ended"
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                Log.e(TAG, "⚠️ Agora error: $err")
                timerText.text = "Error: $err"
            }
        }

        override fun onConnectionLost() {
            runOnUiThread {
                Log.e(TAG, "⚠️ Connection lost")
                timerText.text = "Connection lost"
            }
        }

        override fun onConnectionInterrupted() {
            runOnUiThread {
                Log.e(TAG, "⚠️ Connection interrupted")
                timerText.text = "Reconnecting..."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_page)

        // Handle back press
        onBackPressedDispatcher.addCallback(this) {
            endCall()
        }

        // Initialize views
        muteBtn = findViewById(R.id.mute_audio_button)
        endCallBtn = findViewById(R.id.endCall)
        actionBtn = findViewById(R.id.action_button)
        timerText = findViewById(R.id.timer)
        nameText = findViewById(R.id.name)
        avatarText = findViewById(R.id.avatar_text)

        // Get intent data
        callId = intent.getStringExtra("CALL_ID") ?: ""
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        otherUserName = intent.getStringExtra("USER_NAME") ?: "Unknown"
        otherUserId = intent.getStringExtra("USER_ID") ?: ""
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)
        isOutgoing = intent.getBooleanExtra("IS_OUTGOING", true)

        Log.d(TAG, "=== Call Page Started ===")
        Log.d(TAG, "Call ID: $callId")
        Log.d(TAG, "Channel: $channelName")
        Log.d(TAG, "Other user: $otherUserName ($otherUserId)")
        Log.d(TAG, "Video call: $isVideoCall")
        Log.d(TAG, "Outgoing: $isOutgoing")

        if (channelName.isEmpty()) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up UI
        nameText.text = otherUserName
        timerText.text = if (isOutgoing) "Calling..." else "Connecting..."

        // Set avatar initials
        val initials = if (otherUserName.isNotEmpty()) {
            otherUserName.split(" ").take(2).map { it.first() }.joinToString("").uppercase()
        } else "?"
        avatarText.text = initials

        // Set up button listeners
        endCallBtn.setOnClickListener { endCall() }
        muteBtn.setOnClickListener { toggleMute() }
        actionBtn.setOnClickListener { toggleSpeaker() }

        // Register broadcast receivers
        registerReceivers()

        // Check permissions and join
        if (checkPermissions()) {
            initializeAndJoinChannel()
        } else {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }
    }

    private fun registerReceivers() {
        val answeredFilter = IntentFilter("com.group.i230535_i230048.CALL_ANSWERED")
        val declinedFilter = IntentFilter("com.group.i230535_i230048.CALL_DECLINED")
        val endedFilter = IntentFilter("com.group.i230535_i230048.CALL_ENDED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+, explicitly specify RECEIVER_NOT_EXPORTED
            ContextCompat.registerReceiver(
                this,
                callAnsweredReceiver,
                answeredFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                this,
                callDeclinedReceiver,
                declinedFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            ContextCompat.registerReceiver(
                this,
                callEndedReceiver,
                endedFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            // For older versions
//            registerReceiver(callAnsweredReceiver, answeredFilter)
//            registerReceiver(callDeclinedReceiver, declinedFilter)
//            registerReceiver(callEndedReceiver, endedFilter)
        }
    }

    private fun unregisterReceivers() {
        try {
            unregisterReceiver(callAnsweredReceiver)
            unregisterReceiver(callDeclinedReceiver)
            unregisterReceiver(callEndedReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receivers: ${e.message}")
        }
    }

    private fun checkPermissions(): Boolean {
        return REQUESTED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_ID) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeAndJoinChannel()
            } else {
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeAndJoinChannel() {
        try {
            Log.d(TAG, "Initializing Agora with APP_ID: $APP_ID")

            // Create RtcEngine
            rtcEngine = RtcEngine.create(baseContext, APP_ID, rtcEventHandler)

            Log.d(TAG, "✅ RtcEngine created")

            // Configure for voice call
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.enableAudio()

            if (isVideoCall) {
                rtcEngine?.enableVideo()
                // TODO: Setup video views if needed
                // setupLocalVideo()
            }

            Log.d(TAG, "✅ Audio enabled, joining channel: $channelName")

            // Join channel with random UID
            val uid = (1000..9999).random()
            rtcEngine?.joinChannel(null, channelName, "", uid)

            Log.d(TAG, "📞 Joining with UID: $uid")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - callStartTime
                val seconds = (elapsed / 1000) % 60
                val minutes = (elapsed / 1000) / 60
                timerText.text = String.format("%02d:%02d", minutes, seconds)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun stopCallTimer() {
        timerRunnable?.let {
            timerHandler.removeCallbacks(it)
        }
        timerRunnable = null
    }

    private fun toggleMute() {
        muted = !muted
        rtcEngine?.muteLocalAudioStream(muted)

        // Update icon (use your actual drawable resources)
        // val icon = if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        // muteBtn.setImageResource(icon)

        val status = if (muted) "Muted" else "Unmuted"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "🎤 Audio $status")
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        rtcEngine?.setEnableSpeakerphone(speakerOn)

        // Update icon (use your actual drawable resources)
        // val icon = if (speakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_off
        // actionBtn.setImageResource(icon)

        val status = if (speakerOn) "Speaker on" else "Speaker off"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "🔊 Speaker $status")
    }

    private fun endCall() {
        Log.d(TAG, "Ending call...")

        stopCallTimer()

        // Notify other party that call ended
        if (callId.isNotEmpty()) {
            CallManager.endCall(this, callId)
        }

        // Leave Agora channel
        rtcEngine?.leaveChannel()
        Thread {
            RtcEngine.destroy()
            rtcEngine = null
        }.start()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity destroyed")

        stopCallTimer()
        unregisterReceivers()

        rtcEngine?.leaveChannel()
        Thread {
            RtcEngine.destroy()
        }.start()
    }
}