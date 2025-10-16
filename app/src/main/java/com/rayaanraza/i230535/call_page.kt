package com.rayaanraza.i230535

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class call_page : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase

    private lateinit var muteBtn: ImageView
    private lateinit var endCallBtn: ImageView
    private lateinit var actionBtn: ImageView
    private lateinit var timerText: TextView
    private lateinit var nameText: TextView
    private lateinit var avatarText: TextView

    private var muted = false
    private var speakerOn = false
    private var channelName: String = ""
    private var otherUserName: String = ""
    private var otherUserId: String = ""
    private var isVideoCall: Boolean = false

    private val APP_ID = "e9d3c619be27400fb63d6293be8bf820"
    private var rtcEngine: RtcEngine? = null

    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                timerText.text = "Connected"
                Log.d("AgoraCall", "✅ Joined channel: $channel, uid: $uid")
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                timerText.text = "Call in progress..."
                Log.d("AgoraCall", "👤 Remote user joined: $uid")
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                timerText.text = "Call ended"
                Log.d("AgoraCall", "❌ Remote user left: $uid")
                // End call after short delay
                timerText.postDelayed({ endCall() }, 1500)
            }
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            runOnUiThread {
                timerText.text = "Call ended"
                Log.d("AgoraCall", "📴 Left channel")
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                timerText.text = "Error: $err"
                Log.e("AgoraCall", "⚠️ Agora error: $err")
            }
        }

        override fun onConnectionLost() {
            runOnUiThread {
                timerText.text = "Connection lost"
                Log.e("AgoraCall", "⚠️ Connection lost")
            }
        }

        override fun onConnectionInterrupted() {
            runOnUiThread {
                timerText.text = "Connection interrupted"
                Log.e("AgoraCall", "⚠️ Connection interrupted")
            }
        }
    }

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_page)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Initialize views - MATCHING YOUR XML IDs
        muteBtn = findViewById(R.id.mute_audio_button)
        endCallBtn = findViewById(R.id.endCall)
        actionBtn = findViewById(R.id.action_button)
        timerText = findViewById(R.id.timer)
        nameText = findViewById(R.id.name)
        avatarText = findViewById(R.id.avatar_text)

        // Get intent data
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        otherUserName = intent.getStringExtra("USER_NAME") ?: "Unknown"
        otherUserId = intent.getStringExtra("USER_ID") ?: ""
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        Log.d("AgoraCall", "=== Call Started ===")
        Log.d("AgoraCall", "Channel: $channelName")
        Log.d("AgoraCall", "Other user: $otherUserName ($otherUserId)")
        Log.d("AgoraCall", "Video call: $isVideoCall")

        if (channelName.isEmpty()) {
            Toast.makeText(this, "Invalid call data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up UI
        nameText.text = otherUserName
        timerText.text = "Calling..."

        // Set avatar initials
        val initials = if (otherUserName.isNotEmpty()) {
            otherUserName.split(" ").take(2).map { it.first() }.joinToString("").uppercase()
        } else "?"
        avatarText.text = initials

        // Set up button listeners
        endCallBtn.setOnClickListener { endCall() }
        muteBtn.setOnClickListener { toggleMute() }
        actionBtn.setOnClickListener { toggleSpeaker() }

        // Check permissions and join
        if (checkPermissions()) {
            initializeAndJoinChannel()
        } else {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
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
            Log.d("AgoraCall", "Initializing Agora with APP_ID: $APP_ID")

            // Create RtcEngine
            rtcEngine = RtcEngine.create(baseContext, APP_ID, rtcEventHandler)

            Log.d("AgoraCall", "✅ RtcEngine created")

            // Configure for voice call (IMPORTANT for two-way audio)
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.enableAudio()

            if (isVideoCall) {
                rtcEngine?.enableVideo()
                // Setup video views if needed
                // setupLocalVideo()
            }

            Log.d("AgoraCall", "✅ Audio enabled, joining channel: $channelName")

            // Join channel with random UID
            val uid = (1000..9999).random()
            rtcEngine?.joinChannel(null, channelName, "", uid)

            Log.d("AgoraCall", "📞 Joining with UID: $uid")

        } catch (e: Exception) {
            Log.e("AgoraCall", "❌ Failed to initialize: ${e.message}", e)
            Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun toggleMute() {
        muted = !muted
        rtcEngine?.muteLocalAudioStream(muted)

        val icon = if (muted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        muteBtn.setImageResource(icon)

        val status = if (muted) "Muted" else "Unmuted"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        Log.d("AgoraCall", "🎤 Audio $status")
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        rtcEngine?.setEnableSpeakerphone(speakerOn)

        val icon = if (speakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_off
        actionBtn.setImageResource(icon)

        val status = if (speakerOn) "Speaker on" else "Speaker off"
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        Log.d("AgoraCall", "🔊 Speaker $status")
    }

    private fun endCall() {
        Log.d("AgoraCall", "Ending call...")

        // Update Firebase call status
        CallManager.endCall(channelName)

        rtcEngine?.leaveChannel()
        Thread {
            RtcEngine.destroy()
            rtcEngine = null
        }.start()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AgoraCall", "Activity destroyed")
        rtcEngine?.leaveChannel()
        Thread {
            RtcEngine.destroy()
        }.start()
    }
}