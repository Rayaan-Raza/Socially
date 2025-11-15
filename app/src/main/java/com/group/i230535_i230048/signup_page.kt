package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context // CHANGED: Added for SharedPreferences
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import java.io.ByteArrayOutputStream

class signup_page : AppCompatActivity() {

    // REMOVED: private lateinit var auth: FirebaseAuth
    private lateinit var profileCircle: FrameLayout
    private lateinit var profileImage: ImageView
    private var selectedImageBase64: String? = null

    private val PICK_IMAGE = 101

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_page)

        // REMOVED: auth = FirebaseAuth.getInstance()

        findViewById<ImageView>(R.id.left_arrow).setOnClickListener {
            startActivity(Intent(this, login_sign::class.java))
            finish()
        }

        profileCircle = findViewById(R.id.Profile_circle)
        profileImage = findViewById(R.id.profile_image)

        profileCircle.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE)
        }

        val etUsername = findViewById<EditText>(R.id.Username_input)
        val etFirst = findViewById<EditText>(R.id.your_name_input)
        val etLast = findViewById<EditText>(R.id.last_name_input)
        val etDob = findViewById<EditText>(R.id.date_of_birth_input)
        val etEmail = findViewById<EditText>(R.id.email_input)
        val etPass = findViewById<EditText>(R.id.password_input)

        findViewById<MaterialButton>(R.id.create_account).setOnClickListener {
            val username = etUsername.text.toString().trim()
            val first = etFirst.text.toString().trim()
            val last = etLast.text.toString().trim()
            val dob = etDob.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pass = etPass.text.toString()

            if (username.isEmpty() || first.isEmpty() || last.isEmpty() ||
                dob.isEmpty() || email.isEmpty() || pass.length < 6) {
                Toast.makeText(this, "Fill all fields (password ≥ 6 chars).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // --- CHANGED: Replaced entire Firebase block with Volley ---

            val queue = Volley.newRequestQueue(this)
            val url = AppGlobals.BASE_URL + "signup.php" // [cite: 151-154]

            val stringRequest = object : StringRequest(
                Request.Method.POST,
                url,
                { response ->
                    // Success listener
                    try {
                        val jsonResponse = JSONObject(response)
                        val success = jsonResponse.getBoolean("success") // [cite: 111, 116]

                        if (success) {
                            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()

                            // Parse the user data from response
                            val userObject = jsonResponse.getJSONObject("data") // [cite: 113, 177]
                            val uid = userObject.getString("uid") // [cite: 123]
                            val newUsername = userObject.getString("username") // [cite: 126]

                            // Save session to log the user in
                            val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit().putString(AppGlobals.KEY_USER_UID, uid).apply()
                            prefs.edit().putString(AppGlobals.KEY_USERNAME, newUsername).apply()

                            // Go to home page
                            startActivity(Intent(this, home_page::class.java))
                            finish()

                        } else {
                            // API returned an error (e.g., "Email or username already in use")
                            val message = jsonResponse.getString("message") // [cite: 112, 186, 191]
                            Toast.makeText(this, "Signup failed: $message", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                { error ->
                    // Network error listener
                    Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
                }) {

                // Override getParams to send data as x-www-form-urlencoded
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["email"] = email       // [cite: 159]
                    params["password"] = pass     // [cite: 160]
                    params["username"] = username // [cite: 161]
                    params["firstName"] = first   // [cite: 162]
                    params["lastName"] = last     // [cite: 163]
                    params["dob"] = dob           // [cite: 164]

                    // Add optional profile picture if user selected one
                    selectedImageBase64?.let {
                        params["profilePictureBase64"] = it // [cite: 171]
                    }
                    return params
                }
            }
            // Add the request to the queue
            queue.add(stringRequest)
            // --- END OF CHANGED BLOCK ---
        }
    }

    // REMOVED: setUserOnlineStatus function (backend handles this)

    // This image picker logic is perfect, no changes needed.
    // It already provides the Base64 string that the API needs.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    val source = ImageDecoder.createSource(this.contentResolver, imageUri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                }
                profileImage.setImageBitmap(bitmap)
                selectedImageBase64 = encodeImage(bitmap)
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        // Reduced quality to 50 to avoid request payload being too large
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }
}