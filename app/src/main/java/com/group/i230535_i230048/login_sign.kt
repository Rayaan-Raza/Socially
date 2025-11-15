package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.Context // CHANGED: Added for SharedPreferences
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
// REMOVED: import com.google.firebase.auth.FirebaseAuth

// CHANGED: Central place for your SharedPreferences keys
object AppGlobals {
    const val PREFS_NAME = "SociallyPrefs"
    const val KEY_USER_UID = "user_uid"
    const val KEY_USERNAME = "username"
    const val KEY_PROFILE_COMPLETE = "profile_complete" // ADDED THIS

    // TODO: Replace with your actual server URL from Dev A
    const val BASE_URL = "https://YOUR_SUBDOMAIN.infinityfreeapp.com/"

    // SET THIS TO TRUE TO TEST WITHOUT A REAL BACKEND
    const val IS_TESTING_MODE = true
}

class login_sign : AppCompatActivity() {

    // REMOVED: private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_sign)

        // REMOVED: auth = FirebaseAuth.getInstance()

        // CHANGED: Session check now uses SharedPreferences
        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val savedUid = prefs.getString(AppGlobals.KEY_USER_UID, null)

        if (savedUid != null) {
            startActivity(Intent(this, home_page::class.java))
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // NOTE: This logic remains the same. It just navigates.
        findViewById<MaterialButton>(R.id.log_in).setOnClickListener {
            startActivity(Intent(this, switch_account::class.java))
        }

        findViewById<TextView>(R.id.switch_acc).setOnClickListener {
            startActivity(Intent(this, switch_account::class.java))
        }

        findViewById<TextView>(R.id.Sign_up).setOnClickListener {
            startActivity(Intent(this, signup_page::class.java))
            finish()
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}