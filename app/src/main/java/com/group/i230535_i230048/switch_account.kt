package com.group.i230535_i230048

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class switch_account : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_account)

        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.username)   // Using "Username" field as Email
        val etPassword = findViewById<EditText>(R.id.password)
        val btnLogin = findViewById<MaterialButton>(R.id.log_in)
        val tvSignup = findViewById<TextView>(R.id.Sign_up)
        val tvForgot = findViewById<TextView>(R.id.forgot_password)

        tvSignup.setOnClickListener {
            startActivity(Intent(this, signup_page::class.java))
            finish()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()
            val pass = etPassword.text.toString().trim() // Also trim password

            // --- IMPROVED VALIDATION ---
            if (email.isEmpty()) {
                etEmail.error = "Email is required."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (pass.isEmpty()) {
                etPassword.error = "Password is required."
                etPassword.requestFocus()
                return@setOnClickListener
            }
            // --- END OF VALIDATION ---

            // Show feedback that a login attempt is in progress (optional but good UX)
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid
                    if (uid == null) {
                        Toast.makeText(this, "Login failed: Could not get user ID.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // After successful login, check if the user's profile is complete
                    val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                    userRef.get()
                        .addOnSuccessListener { dataSnapshot ->
                            val isProfileComplete = dataSnapshot.child("profileCompleted").getValue(Boolean::class.java) ?: false

                            // Navigate based on profile completion status
                            val nextActivity = if (isProfileComplete) home_page::class.java else signup_page::class.java

                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, nextActivity)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish() // Ensures user cannot go back to the login screen
                        }
                        .addOnFailureListener { databaseException ->
                            // Handle case where login is successful but reading from database fails
                            Toast.makeText(this, "Login successful, but failed to retrieve profile data: ${databaseException.message}", Toast.LENGTH_LONG).show()
                            // Stay on login screen to prevent letting user into a broken state
                        }
                }
                .addOnFailureListener { exception ->
                    // Handle login failures (e.g., wrong password, user not found)
                    Toast.makeText(this, "Login failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }

        tvForgot.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Enter your email to reset password."
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Please enter a valid email address."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset email sent to $email.", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send reset email: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
