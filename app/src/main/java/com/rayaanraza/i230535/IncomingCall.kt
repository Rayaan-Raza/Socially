package com.rayaanraza.i230535

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var callId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        callId = intent.getStringExtra("CALL_ID")
        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        val isVideoCall = intent.getBooleanExtra("IS_VIDEO_CALL", false)

        // Setup UI
        findViewById<TextView>(R.id.caller_name).text = callerName
        findViewById<TextView>(R.id.call_type).text =
            if (isVideoCall) "Incoming video call..." else "Incoming voice call..."

        // Avatar text (first letters of name)
        val avatarText = callerName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.toString()?.uppercase() }
            .joinToString("")
        findViewById<TextView>(R.id.caller_avatar_text).text = avatarText

        // Accept call button
        findViewById<ImageView>(R.id.accept_call).setOnClickListener {
            stopRingtone()
            callId?.let { id ->
                CallManager.acceptCall(this, id) {
                    finish()
                }
            }
        }

        // Decline call button
        findViewById<ImageView>(R.id.decline_call).setOnClickListener {
            stopRingtone()
            callId?.let { id ->
                CallManager.declineCall(id)
            }
            finish()
        }

        // Play ringtone
        playRingtone()
    }

    private fun playRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }
}