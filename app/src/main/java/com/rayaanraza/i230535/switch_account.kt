package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class switch_account : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_account)

        auth = FirebaseAuth.getInstance()

        val etEmail    = findViewById<EditText>(R.id.username)   // Using "Username" field as Email
        val etPassword = findViewById<EditText>(R.id.password)
        val btnLogin   = findViewById<MaterialButton>(R.id.log_in)
        val tvSignup   = findViewById<TextView>(R.id.Sign_up)
        val tvForgot   = findViewById<TextView>(R.id.forgot_password)

        tvSignup.setOnClickListener {
            startActivity(Intent(this, signup_page::class.java))
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass  = etPassword.text.toString()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Enter email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener {
                    val uid = auth.currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(this, "Login failed: UID missing.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // Read profileCompleted from Realtime DB
                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                    userRef.get()
                        .addOnSuccessListener { snap ->
                            val completed = snap.child("profileCompleted").getValue(Boolean::class.java) ?: false
                            val next = if (completed) home_page::class.java else signup_page::class.java
                            startActivity(Intent(this, next))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Could not read profile: ${e.message}", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, home_page::class.java))
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        tvForgot.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset email sent.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Could not send reset email: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
