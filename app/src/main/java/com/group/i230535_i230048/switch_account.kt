package com.group.i230535_i230048

import android.content.Context // CHANGED
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
// CHANGED: Added Volley and JSON imports
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
// REMOVED: Firebase imports
// import com.google.firebase.auth.FirebaseAuth
// import com.google.firebase.database.FirebaseDatabase

class switch_account : AppCompatActivity() {

    // REMOVED: private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_account)

        // REMOVED: auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.username)
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
            val pass = etPassword.text.toString().trim()

            // --- VALIDATION (No changes) ---
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

            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            // --- CHANGED: Replaced Firebase block with Volley ---

            if (AppGlobals.IS_TESTING_MODE) {
                // --- THIS IS YOUR TEST BLOCK ---
                // We'll simulate a successful login for a user whose profile is NOT complete
                val mockResponse = """
                {
                    "success": true,
                    "message": "Login successful (MOCK)",
                    "data": {
                        "id": 1,
                        "uid": "k29d81f7f9a4e3b5c71f2a90d1f2c3de",
                        "firebase_uid": "k29d81f7f9a4e3b5c71f2a90d1f2c3de",
                        "email": "$email",
                        "username": "mock_user",
                        "firstName": "Mock",
                        "lastName": "User",
                        "fullName": "Mock User",
                        "dob": "2000-01-01",
                        "bio": "Hey there! I'm using Socially",
                        "website": "",
                        "phoneNumber": "",
                        "gender": "",
                        "profilePictureUrl": "",
                        "photo": "",
                        "fcmToken": "",
                        "followersCount": 0,
                        "followingCount": 0,
                        "postsCount": 0,
                        "accountPrivate": 0,
                        "profileCompleted": 0,
                        "isOnline": 1,
                        "lastSeen": 1710000000000,
                        "createdAt": 1710000000000
                    }
                }
                """
                // Manually call the function that handles the response
                handleLoginResponse(mockResponse)

            } else {
                // --- THIS IS YOUR REAL NETWORK CODE ---
                val queue = Volley.newRequestQueue(this)
                val url = AppGlobals.BASE_URL + "login.php" // [cite: 193-194]

                val stringRequest = object : StringRequest(
                    Request.Method.POST, //
                    url,
                    { response ->
                        // Success: Pass the real response to the handler
                        handleLoginResponse(response)
                    },
                    { error ->
                        // Error
                        Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
                    }) {

                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params["email"] = email //
                        params["password"] = pass //
                        return params
                    }
                }
                queue.add(stringRequest)
            }
            // --- END OF CHANGED BLOCK ---
        }

        tvForgot.setOnClickListener {
            // --- CHANGED: "Forgot Password" ---
            // The provided API doc does not have an endpoint for password reset.
            // You must ask Dev A to create a "forgot_password.php" endpoint.
            // For now, this feature cannot be implemented.
            Toast.makeText(this, "Feature not available yet.", Toast.LENGTH_LONG).show()

            /*
            // OLD FIREBASE CODE:
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) { ... }
            auth.sendPasswordResetEmail(email) ...
            */
        }
    }

    /**
     * A new function to handle the login response,
     * whether it's from the real network or your mock test.
     */
    private fun handleLoginResponse(response: String) {
        try {
            val jsonResponse = JSONObject(response)
            if (jsonResponse.getBoolean("success")) { // [cite: 206]
                // Login successful
                val userObject = jsonResponse.getJSONObject("data") // [cite: 208]

                // Get the data from the API response
                val uid = userObject.getString("uid") // [cite: 123]
                val username = userObject.getString("username") // [cite: 126]
                // The API sends profileCompleted as an Int (0 or 1) [cite: 142]
                val isProfileComplete = userObject.getInt("profileCompleted") == 1

                // Save the session
                val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE).edit()
                prefs.putString(AppGlobals.KEY_USER_UID, uid)
                prefs.putString(AppGlobals.KEY_USERNAME, username)
                prefs.putBoolean(AppGlobals.KEY_PROFILE_COMPLETE, isProfileComplete)
                prefs.apply()

                // Navigate based on profile completion status
                // This matches your original app's logic
                val nextActivity = if (isProfileComplete) home_page::class.java else signup_page::class.java

                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, nextActivity)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } else {
                // Login failed (e.g., "Invalid email or password") [cite: 222]
                val message = jsonResponse.getString("message")
                Toast.makeText(this, "Login failed: $message", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing login response: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}