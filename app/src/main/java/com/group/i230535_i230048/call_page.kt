package com.group.i230535_i230048

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

class call_page : AppCompatActivity() {

    companion object {
        private const val TAG = "CallPage"
        private const val PERMISSION_REQ_ID = 22
        private val REQUESTED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    // UI Elements
    private lateinit var remoteVideoContainer: FrameLayout
    private lateinit var localVideoCard: CardView
    private lateinit var localVideoFrame: FrameLayout
    private lateinit var voiceCallUi: LinearLayout

    private lateinit var muteBtn: ImageView
    private lateinit var endCallBtn: ImageView
    private lateinit var actionBtn: ImageView // Speaker or Video Toggle
    private lateinit var timerText: TextView
    private lateinit var nameText: TextView
    private lateinit var avatarText: TextView

    // State
    private var muted = false
    private var speakerOn = false
    private var isVideoCall = false

    // Agora
    private val APP_ID = "e9d3c619be27400fb63d6293be8bf820" // Make sure this matches your Dashboard
    private var rtcEngine: RtcEngine? = null
    private var channelName: String = ""

    // Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callStartTime: Long = 0
    private var timerRunnable: Runnable? = null

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                Log.d(TAG, "✅ Joined Channel Success: $uid")
                timerText.text = "Waiting..."
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                Log.d(TAG, "👤 Remote user joined: $uid")
                timerText.text = "Connected"
                startCallTimer()

                // Show their video if it's a video call
                if (isVideoCall) {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(applicationContext, "User left call", Toast.LENGTH_SHORT).show()
                endCall()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_page)

        // 1. Initialize Views
        remoteVideoContainer = findViewById(R.id.remote_video_view_container)
        localVideoCard = findViewById(R.id.local_video_view_container) // The CardView wrapper
        localVideoFrame = findViewById(R.id.local_video_frame)         // The actual FrameLayout
        voiceCallUi = findViewById(R.id.voice_call_ui)

        muteBtn = findViewById(R.id.mute_audio_button)
        endCallBtn = findViewById(R.id.endCall)
        actionBtn = findViewById(R.id.action_button)
        timerText = findViewById(R.id.timer)
        nameText = findViewById(R.id.name)
        avatarText = findViewById(R.id.avatar_text)

        // 2. Get Intent Data
        channelName = intent.getStringExtra("CHANNEL_NAME") ?: ""
        val userName = intent.getStringExtra("USER_NAME") ?: "Unknown"
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        Log.d(TAG, "Starting Call - Channel: $channelName, Video: $isVideoCall")

        // 3. Setup Initial UI
        nameText.text = userName
        val initials = if (userName.isNotEmpty()) userName.take(2).uppercase() else "??"
        avatarText.text = initials

        if (isVideoCall) {
            // Video Mode: Hide voice UI, Show local video card
            voiceCallUi.visibility = View.GONE
            localVideoCard.visibility = View.VISIBLE
            actionBtn.setImageResource(R.drawable.ic_switch_camera) // Change icon if you have one
        } else {
            // Audio Mode: Show voice UI, Hide local video card
            voiceCallUi.visibility = View.VISIBLE
            localVideoCard.visibility = View.GONE
        }

        // 4. Listeners
        endCallBtn.setOnClickListener { endCall() }
        muteBtn.setOnClickListener { toggleMute() }

        actionBtn.setOnClickListener {
            if (isVideoCall) {
                rtcEngine?.switchCamera() // Switch Front/Back camera
            } else {
                toggleSpeaker() // Toggle Speakerphone
            }
        }

        // 5. Permissions & Engine Start
        if (checkPermissions()) {
            initializeAndJoinChannel()
        } else {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }
    }

    private fun initializeAndJoinChannel() {
        try {
            Log.d(TAG, "Initializing Agora Engine...")
            rtcEngine = RtcEngine.create(baseContext, APP_ID, rtcEventHandler)

            if (isVideoCall) {
                Log.d(TAG, "Setting up Video Mode")
                rtcEngine?.enableVideo()

                // Setup YOUR camera
                setupLocalVideo()

                // Default to speaker for video calls
                rtcEngine?.setEnableSpeakerphone(true)
                speakerOn = true
            } else {
                Log.d(TAG, "Setting up Audio Mode")
                rtcEngine?.enableAudio()
                // Default to earpiece for audio calls (optional)
                rtcEngine?.setEnableSpeakerphone(false)
                speakerOn = false
            }

            // Join
            val res = rtcEngine?.joinChannel(null, channelName, "", 0)
            if (res != 0) {
                Log.e(TAG, "Join Channel Failed: $res")
                Toast.makeText(this, "Failed to join call", Toast.LENGTH_SHORT).show()
                finish()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora: ${e.message}")
            Toast.makeText(this, "Error initializing call", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupLocalVideo() {
        // Create SurfaceView
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        surfaceView.setZOrderMediaOverlay(true) // Important! Renders on top

        // Add to FrameLayout inside the CardView
        localVideoFrame.addView(surfaceView)

        // Tell Agora to render local stream here
        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    private fun setupRemoteVideo(uid: Int) {
        if (remoteVideoContainer.childCount > 0) {
            remoteVideoContainer.removeAllViews()
        }

        // Create SurfaceView
        val surfaceView = RtcEngine.CreateRendererView(baseContext)
        remoteVideoContainer.addView(surfaceView)

        // Tell Agora to render remote stream here
        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                timerText.text = String.format("%02d:%02d", min, sec)
                timerHandler.postDelayed(this, 1000)
            }
        }
        timerHandler.post(timerRunnable!!)
    }

    private fun toggleMute() {
        muted = !muted
        rtcEngine?.muteLocalAudioStream(muted)
        val iconAlpha = if (muted) 0.5f else 1.0f
        muteBtn.alpha = iconAlpha
        Toast.makeText(this, if (muted) "Muted" else "Unmuted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSpeaker() {
        speakerOn = !speakerOn
        rtcEngine?.setEnableSpeakerphone(speakerOn)
        Toast.makeText(this, if (speakerOn) "Speaker On" else "Speaker Off", Toast.LENGTH_SHORT).show()
    }

    private fun endCall() {
        Log.d(TAG, "Ending Call")
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        rtcEngine?.leaveChannel()

        // Destroy in background to prevent UI lag
        Thread {
            RtcEngine.destroy()
            rtcEngine = null
        }.start()

        finish()
    }

    private fun checkPermissions(): Boolean {
        return REQUESTED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(req: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(req, perms, res)
        if (req == PERMISSION_REQ_ID && res.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializeAndJoinChannel()
        } else {
            Toast.makeText(this, "Permissions required for call", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure engine is destroyed if activity is killed
        if (rtcEngine != null) {
            endCall()
        }
    }
}