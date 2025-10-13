package com.rayaanraza.i230535

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class home_page : AppCompatActivity() {

    private var isPostLiked = false
    // --- NEW: Add Firebase variables ---
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // --- NEW: Define view variables ---
    private lateinit var storyProfileImage: ImageView
    private lateinit var navProfileImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)
        // Note: You had setContentView twice, it has been removed.

        // --- NEW: Initialize Firebase ---
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // --- NEW: Initialize Views ---
        // These are the ImageViews we need to update
        storyProfileImage = findViewById(R.id.pfp1)
        navProfileImage = findViewById(R.id.profile)

        // Apply window insets to the main layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- NEW: Load user data ---
        loadCurrentUserProfilePicture()

        // --- Setup all your existing click listeners ---
        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.like).setOnClickListener { postLikeImageView ->
            isPostLiked = !isPostLiked
            (postLikeImageView as ImageView).setImageResource(
                if (isPostLiked) R.drawable.liked else R.drawable.like
            )
        }

        findViewById<ImageView>(R.id.heart).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
        }

        findViewById<FrameLayout>(R.id.story_clickable).setOnClickListener {
            startActivity(Intent(this, story_view::class.java))
            // Using finish() here might be undesirable as it prevents returning to home
        }

        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
        }

        findViewById<ImageView>(R.id.dms).setOnClickListener {
            startActivity(Intent(this, dms::class.java))
        }

        findViewById<ImageView>(R.id.camera).setOnClickListener {
            startActivity(Intent(this, camera::class.java))
        }

        findViewById<ImageView>(R.id.post).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
        }

        findViewById<ImageView>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
        }
    }

    // --- NEW: Function to load user data from Firebase ---
    private fun loadCurrentUserProfilePicture() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // If user is not logged in, maybe show a default image or do nothing
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = database.getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Get the profile picture URL (Base64 string) from the database
                    val base64Image = snapshot.child("profilePictureUrl").getValue(String::class.java)

                    if (!base64Image.isNullOrEmpty()) {
                        try {
                            val bitmap = decodeBase64(base64Image)
                            // Set the decoded image to BOTH ImageViews
                            storyProfileImage.setImageBitmap(bitmap)
                            navProfileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            // Handle error if the Base64 string is invalid
                            Toast.makeText(this@home_page, "Failed to decode profile image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@home_page, "Failed to load user data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- NEW: Utility function to decode the image ---
    private fun decodeBase64(base64: String): Bitmap {
        val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
}
