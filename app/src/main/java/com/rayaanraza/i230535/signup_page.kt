package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class signup_page : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_page)

        auth = FirebaseAuth.getInstance()

        findViewById<ImageView>(R.id.left_arrow).setOnClickListener {
            startActivity(Intent(this, login_sign::class.java))
            finish()
        }

        val etUsername = findViewById<EditText>(R.id.Username_input)
        val etFirst    = findViewById<EditText>(R.id.your_name_input)
        val etLast     = findViewById<EditText>(R.id.last_name_input)
        val etDob      = findViewById<EditText>(R.id.date_of_birth_input)
        val etEmail    = findViewById<EditText>(R.id.email_input)
        val etPass     = findViewById<EditText>(R.id.password_input)

        findViewById<MaterialButton>(R.id.create_account).setOnClickListener {
            val username = etUsername.text.toString().trim()
            val first    = etFirst.text.toString().trim()
            val last     = etLast.text.toString().trim()
            val dob      = etDob.text.toString().trim()
            val email    = etEmail.text.toString().trim()
            val pass     = etPass.text.toString()

            if (username.isEmpty() || first.isEmpty() || last.isEmpty() ||
                dob.isEmpty() || email.isEmpty() || pass.length < 6) {
                Toast.makeText(this, "Fill all fields (password ≥ 6 chars).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Step 1 — Create Auth user
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        Toast.makeText(this, "Signup failed: UID missing.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    // Step 2 — Create user data
                    val profile = HashMap<String, Any>()
                    profile["uid"] = uid
                    profile["username"] = username
                    profile["firstName"] = first
                    profile["lastName"] = last
                    profile["dob"] = dob
                    profile["email"] = email
                    profile["createdAt"] = System.currentTimeMillis()
                    profile["profileCompleted"] = true

                    // Step 3 — Write to Realtime DB
                    val usersRef = FirebaseDatabase.getInstance().getReference("users")
                    usersRef.child(uid).setValue(profile)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Welcome $username!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, home_page::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
