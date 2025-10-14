package com.rayaanraza.i230535

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import java.util.Locale

class call_page : AppCompatActivity() {

    private val APP_ID = "e9d3c619be27400fb63d6293be8bf820"
    private var rtcEngine: RtcEngine? = null
    private var channelName: String? = null
    private var isVideoCall: Boolean = false

    private lateinit var timerText: TextView
    private lateinit var remoteVideoContainer: FrameLayout
    private lateinit var localVideoContainer: FrameLayout
    private lateinit var voiceCallUI: LinearLayout
    private lateinit var actionButton: ImageView
    private lateinit var muteButton: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var isCallConnected = false
    private var isMuted = false
    private var isSpeakerOn = false

    private val PERMISSION_REQ_ID = 22
    private val REQUESTED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA
    )

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                isCallConnected = true
                startTimer()
                if (isVideoCall) {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(this@call_page, "Call ended", Toast.LENGTH_SHORT).show()
                endCall()
            }
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                Toast.makeText(this@call_page, "Joined channel successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_page)

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        channelName = intent.getStringExtra("CHANNEL_NAME")
        val otherUserName = intent.getStringExtra("USER_NAME")
        isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        if (channelName.isNullOrEmpty()) {
            Toast.makeText(this, "Call information missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews(otherUserName)
        setupCallControls()

        if (checkPermissions()) {
            initializeAndJoinChannel()
        } else {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, PERMISSION_REQ_ID)
        }
    }

    private fun initViews(userName: String?) {
        findViewById<TextView>(R.id.name).text = userName ?: "Calling..."

        val avatarText = userName?.split(" ")
            ?.take(2)
            ?.mapNotNull { it.firstOrNull()?.toString()?.uppercase() }
            ?.joinToString("") ?: "U"
        findViewById<TextView>(R.id.avatar_text).text = avatarText

        timerText = findViewById(R.id.timer)
        remoteVideoContainer = findViewById(R.id.remote_video_view_container)
        localVideoContainer = findViewById(R.id.local_video_view_container)
        voiceCallUI = findViewById(R.id.voice_call_ui)
        actionButton = findViewById(R.id.action_button)
        muteButton = findViewById(R.id.mute_audio_button)

        if (isVideoCall) {
            voiceCallUI.visibility = View.GONE
            actionButton.setImageResource(R.drawable.ic_switch_camera)
        } else {
            localVideoContainer.visibility = View.GONE
            remoteVideoContainer.visibility = View.GONE
            actionButton.setImageResource(R.drawable.ic_speaker_on)
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
                Toast.makeText(this, "Permissions required for this feature.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initializeAndJoinChannel() {
        try {
            val config = RtcEngineConfig()
            config.mContext = baseContext
            config.mAppId = APP_ID
            config.mEventHandler = mRtcEventHandler
            rtcEngine = RtcEngine.create(config)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Agora SDK failed to initialize. Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        if (isVideoCall) {
            rtcEngine?.enableVideo()
            setupLocalVideo()
        } else {
            rtcEngine?.disableVideo()
        }

        // Always enable audio
        rtcEngine?.enableAudio()

        val options = ChannelMediaOptions()
        options.channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

        // Join channel with token (use null for testing, get real token in production)
        rtcEngine?.joinChannel(null, channelName, 0, options)
    }

    private fun setupLocalVideo() {
        try {
            // Create texture view for local video
            val surfaceView = RtcEngine.CreateRendererView(baseContext)
            surfaceView.setZOrderMediaOverlay(true)

            // Find the local video frame container
            val videoFrame = findViewById<FrameLayout>(R.id.local_video_frame)
            videoFrame.removeAllViews()
            videoFrame.addView(surfaceView)

            // Setup local video canvas
            val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
            rtcEngine?.setupLocalVideo(canvas)
            rtcEngine?.startPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to setup local video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRemoteVideo(uid: Int) {
        runOnUiThread {
            try {
                // Create texture view for remote video
                val surfaceView = RtcEngine.CreateRendererView(baseContext)

                remoteVideoContainer.removeAllViews()
                remoteVideoContainer.addView(surfaceView)

                // Setup remote video canvas
                val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                rtcEngine?.setupRemoteVideo(canvas)

                remoteVideoContainer.visibility = View.VISIBLE
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to setup remote video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCallControls() {
        // End call button
        findViewById<ImageView>(R.id.endCall).setOnClickListener {
            endCall()
        }

        // Mute/Unmute button
        muteButton.setOnClickListener {
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)
            muteButton.setImageResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
            )
            Toast.makeText(
                this,
                if (isMuted) "Microphone muted" else "Microphone unmuted",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Switch camera (video) or speaker (audio) button
        actionButton.setOnClickListener {
            if (isVideoCall) {
                // Switch camera
                rtcEngine?.switchCamera()
                Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
            } else {
                // Toggle speaker
                isSpeakerOn = !isSpeakerOn
                rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
                actionButton.setImageResource(
                    if (isSpeakerOn) R.drawable.ic_speaker_on else R.drawable.ic_speaker_off
                )
                Toast.makeText(
                    this,
                    if (isSpeakerOn) "Speaker on" else "Speaker off",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startTimer() {
        val timerRunnable = object : Runnable {
            override fun run() {
                val minutes = seconds / 60
                val secs = seconds % 60
                timerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
                seconds++
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable)
    }

    private fun endCall() {
        handler.removeCallbacksAndMessages(null)
        rtcEngine?.leaveChannel()
        rtcEngine?.stopPreview()
        Thread {
            RtcEngine.destroy()
            rtcEngine = null
        }.start()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        rtcEngine?.leaveChannel()
        rtcEngine?.stopPreview()
        Thread {
            RtcEngine.destroy()
        }.start()
    }
}