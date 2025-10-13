package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    // --- HARDCODED DATA FOR TESTING ---
    private val USER1_UID = "RGA4qIP5gGeidf5a9slHz4b4YMR2" // Corresponds to test1.one@gmail.com
    private val USER1_NAME = "test one"

    private val USER2_UID = "t8CjL2jhCYg82UFOU9zv2qzQnbJ2" // Corresponds to idc@gmail.com
    private val USER2_NAME = "idc idc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        routeUser()
    }

    private fun routeUser() {
        val currentUser = auth.currentUser

        // Case 1: User is not logged in at all
        if (currentUser == null) {
            // Send to the login/signup choice screen
            startActivity(Intent(this, login_sign::class.java))
            finish() // Close MainActivity
            return
        }

        // Case 2: User is logged in, check if their profile is complete
        val usersRef =
            FirebaseDatabase.getInstance().getReference("users").child(currentUser.uid)
        usersRef.get()
            .addOnSuccessListener { dataSnapshot ->
                // Get the profileCompleted flag from the database. Default to 'false' if it doesn't exist.
                val isProfileComplete =
                    dataSnapshot.child("profileCompleted").getValue(Boolean::class.java) ?: false

                // Decide the next activity based on the flag
                val nextActivity = if (isProfileComplete) {
                    // Profile is complete -> Go to the Home Page
                    home_page::class.java
                } else {
                    // Profile is not complete -> Go back to the signup page to finish it
                    signup_page::class.java
                }

                startActivity(Intent(this, nextActivity))
                finish() // Close MainActivity
            }
            .addOnFailureListener { exception ->
                // Case 3: Database error. It's safer to log the user out and send to login.
                Toast.makeText(
                    this,
                    "Failed to verify profile: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                auth.signOut() // Sign out to prevent an inconsistent state
                startActivity(Intent(this, login_sign::class.java))
                finish() // Close MainActivity so the user can't navigate back to it.
            }
    }
}
