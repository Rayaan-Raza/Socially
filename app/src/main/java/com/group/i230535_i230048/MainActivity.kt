package com.group.i230535_i230048

import android.content.Context // CHANGED
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// REMOVED: Firebase imports
// import com.google.firebase.auth.FirebaseAuth
// import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    // REMOVED: private lateinit var auth: FirebaseAuth
    // REMOVED: Hardcoded test data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // REMOVED: auth = FirebaseAuth.getInstance()
        routeUser()
    }

    private fun routeUser() {
        // --- CHANGED: Replaced entire Firebase block with SharedPreferences ---

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val savedUid = prefs.getString(AppGlobals.KEY_USER_UID, null)

        // Case 1: User is not logged in
        if (savedUid == null) {
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }

        // Case 2: User is logged in, check their profile status from preferences
        val isProfileComplete = prefs.getBoolean(AppGlobals.KEY_PROFILE_COMPLETE, false)

        val nextActivity = if (isProfileComplete) {
            // Profile is complete -> Go to the Home Page
            home_page::class.java
        } else {
            // Profile is not complete -> Go back to the signup page
            // (You might want to make a dedicated "complete_profile_page" later)
            signup_page::class.java
        }

        startActivity(Intent(this, nextActivity))
        finish()

        // --- END OF CHANGED BLOCK ---
    }
}