package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.app.Activity
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream

class signup_page : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var profileCircle: FrameLayout
    private lateinit var profileImage: ImageView
    private var selectedImageBase64: String? = null

    private val PICK_IMAGE = 101

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_page)

        auth = FirebaseAuth.getInstance()

        findViewById<ImageView>(R.id.left_arrow).setOnClickListener {
            startActivity(Intent(this, login_sign::class.java))
            finish()
        }

        profileCircle = findViewById(R.id.Profile_circle)
        profileImage = findViewById(R.id.profile_image)

        // Click profile circle to pick image
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

            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        Toast.makeText(this, "Signup failed: UID missing.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val profile = HashMap<String, Any>()
                    profile["uid"] = uid
                    profile["username"] = username
                    profile["firstName"] = first
                    profile["lastName"] = last
                    profile["fullName"] = "$first $last"
                    profile["dob"] = dob
                    profile["email"] = email
                    profile["bio"] = "Hey there! I'm using Socially"
                    profile["website"] = ""
                    profile["phoneNumber"] = ""
                    profile["gender"] = ""
                    profile["createdAt"] = System.currentTimeMillis()
                    profile["profileCompleted"] = true

                    // Online/Offline Status
                    profile["isOnline"] = true
                    profile["lastSeen"] = System.currentTimeMillis()

                    // FCM Token - will be updated later when you add FCM
                    profile["fcmToken"] = ""

                    // Follow System Counters
                    profile["followersCount"] = 0
                    profile["followingCount"] = 0
                    profile["postsCount"] = 0

                    // Privacy Settings
                    profile["accountPrivate"] = false

                    // Profile Picture
                    if (selectedImageBase64 != null) {
                        profile["profilePictureUrl"] = selectedImageBase64!!
                        profile["photo"] = selectedImageBase64!!
                    } else {
                        profile["profilePictureUrl"] = ""
                        profile["photo"] = ""
                    }

                    FirebaseDatabase.getInstance().getReference("users")
                        .child(uid)
                        .setValue(profile)
                        .addOnSuccessListener {
                            // Set user as online
                            setUserOnlineStatus(uid, true)

                            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()
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

    // Set user online status
    private fun setUserOnlineStatus(uid: String, isOnline: Boolean) {
        val statusMap = HashMap<String, Any>()
        statusMap["isOnline"] = isOnline
        statusMap["lastSeen"] = System.currentTimeMillis()

        FirebaseDatabase.getInstance().getReference("users")
            .child(uid)
            .updateChildren(statusMap)
    }

    // Image picker result
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

    // Convert bitmap → Base64 string
    private fun encodeImage(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }
}