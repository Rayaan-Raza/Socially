package com.group.i230535_i230048

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class edit_profile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    // Views
    private lateinit var profileImage: ImageView
    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etWebsite: EditText
    private lateinit var etBio: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etGender: EditText
    private lateinit var btnCancel: TextView
    private lateinit var btnDone: TextView
    private lateinit var txtChangePhoto: TextView

    private var currentUserId: String = ""
    private var newProfileImageBase64: String? = null
    private val PICK_IMAGE = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        userRef = database.getReference("users").child(currentUserId)

        // Initialize all views
        initViews()
        // Set up all click listeners
        setupClickListeners()
        // Load existing user data into the views
        loadUserData()
    }

    private fun initViews() {
        profileImage = findViewById(R.id.img_profile)
        etName = findViewById(R.id.et_name)
        etUsername = findViewById(R.id.et_username)
        etWebsite = findViewById(R.id.et_website)
        etBio = findViewById(R.id.et_bio)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        etGender = findViewById(R.id.et_gender)
        btnCancel = findViewById(R.id.btn_cancel)
        btnDone = findViewById(R.id.btn_done)
        txtChangePhoto = findViewById(R.id.txt_change_photo)
    }

    private fun setupClickListeners() {
        // Cancel button - correctly goes back without saving
        btnCancel.setOnClickListener {
            // setResult(Activity.RESULT_CANCELED) // Optional: for startActivityForResult
            finish()
        }

        // Done button - correctly saves the profile
        btnDone.setOnClickListener {
            saveProfile()
        }

        // Change profile photo text
        txtChangePhoto.setOnClickListener {
            openGallery()
        }

        // Profile image itself can also be clicked
        profileImage.setOnClickListener {
            openGallery()
        }

        // Switch to professional account
        findViewById<TextView>(R.id.btn_switch_pro).setOnClickListener {
            Toast.makeText(this, "Professional account coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    etName.setText(user.fullName)
                    etUsername.setText(user.username)
                    etWebsite.setText(user.website)
                    etBio.setText(user.bio)
                    etEmail.setText(user.email)
                    etPhone.setText(user.phoneNumber)
                    etGender.setText(user.gender)

                    if (!user.profilePictureUrl.isNullOrEmpty()) {
                        try {
                            val bitmap = decodeBase64(user.profilePictureUrl)
                            profileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            // Keep default image if decoding fails
                        }
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfile() {
        val fullName = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val website = etWebsite.text.toString().trim()
        val bio = etBio.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val gender = etGender.text.toString().trim()

        if (fullName.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Name and username are required", Toast.LENGTH_SHORT).show()
            return
        }
        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: fullName
        val lastName = nameParts.getOrNull(1) ?: ""

        val updates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "firstName" to firstName,
            "lastName" to lastName,
            "username" to username,
            "bio" to bio
        )

        // Add optional fields only if they have a value
        if (website.isNotEmpty()) updates["website"] = website
        if (email.isNotEmpty()) updates["email"] = email
        if (phone.isNotEmpty()) updates["phoneNumber"] = phone
        if (gender.isNotEmpty()) updates["gender"] = gender

        // **IMPORTANT**: Add the new profile picture to the updates if it exists
        if (newProfileImageBase64 != null) {
            updates["profilePictureUrl"] = newProfileImageBase64!!
        }

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                // setResult(Activity.RESULT_OK) // Optional: for startActivityForResult
                finish() // Go back to the previous screen (my_profile or view_profile)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data?.data != null) {
            val imageUri: Uri = data.data!!
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    val source = ImageDecoder.createSource(contentResolver, imageUri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }
                profileImage.setImageBitmap(bitmap)
                newProfileImageBase64 = encodeImage(bitmap)
                Toast.makeText(this, "Photo selected. Click Done to save.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        // Compress the image to reduce its size before encoding
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun decodeBase64(base64: String): Bitmap {
        val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
}
