package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class switch_account : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_account)

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

            // Validation
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

            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            if (AppGlobals.IS_TESTING_MODE) {
                // Mock test mode
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
                handleLoginResponse(mockResponse)

            } else {
                // Real network call
                val queue = Volley.newRequestQueue(this)
                val url = AppGlobals.BASE_URL + "login.php"

                val stringRequest = object : StringRequest(
                    Request.Method.POST,
                    url,
                    { response ->
                        handleLoginResponse(response)
                    },
                    { error ->
                        Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
                    }) {

                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params["email"] = email
                        params["password"] = pass
                        return params
                    }
                }
                queue.add(stringRequest)
            }
        }

        tvForgot.setOnClickListener {
            Toast.makeText(this, "Feature not available yet.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleLoginResponse(response: String) {
        try {
            val jsonResponse = JSONObject(response)
            if (jsonResponse.getBoolean("success")) {
                val userObject = jsonResponse.getJSONObject("data")

                val uid = userObject.getString("uid")
                val username = userObject.getString("username")
                val isProfileComplete = userObject.getInt("profileCompleted") == 1

                // Save session
                val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE).edit()
                prefs.putString(AppGlobals.KEY_USER_UID, uid)
                prefs.putString(AppGlobals.KEY_USERNAME, username)
                prefs.putBoolean(AppGlobals.KEY_PROFILE_COMPLETE, isProfileComplete)
                prefs.apply()

                Log.d("Login", "✅ User logged in: $username ($uid)")

                // ========== REGISTER FCM TOKEN FOR CALLS ==========
                CallManager.registerFcmToken(this, uid) { success ->
                    if (success) {
                        Log.d("Login", "✅ FCM token registered - user can receive calls")
                    } else {
                        Log.w("Login", "⚠️ Failed to register FCM token - calls may not work")
                    }
                }
                // ==================================================

                // Navigate based on profile completion
                val nextActivity = if (isProfileComplete) home_page::class.java else signup_page::class.java

                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, nextActivity)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } else {
                val message = jsonResponse.getString("message")
                Toast.makeText(this, "Login failed: $message", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error parsing login response: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}