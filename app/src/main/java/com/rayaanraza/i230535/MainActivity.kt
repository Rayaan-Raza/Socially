package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        routeUser()
    }

    private fun routeUser() {
        val user = auth.currentUser
        if (user == null) {
            // Not logged in → Go to login
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }

        // Logged in → Check if profile completed
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
        usersRef.get()
            .addOnSuccessListener { snapshot ->
                val completed = snapshot.child("profileCompleted").getValue(Boolean::class.java) ?: false
                val next = if (completed) home_page::class.java else signup_page::class.java
                startActivity(Intent(this, next))
                finish()
            }
            .addOnFailureListener {
                startActivity(Intent(this, login_sign::class.java))
                finish()
            }
    }
}
