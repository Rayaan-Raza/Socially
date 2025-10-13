package com.rayaanraza.i230535

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button // Assuming your messageButton is a Button or TextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class view_profile : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase

    // --- Views ---
    private lateinit var profileImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var displayNameTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var postsCountTextView: TextView
    private lateinit var followersCountTextView: TextView
    private lateinit var followingCountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance()
        initializeViews() // Initialize all views first

        // --- THIS IS THE FIX ---
        // Try to get the userId from multiple common keys to ensure compatibility.
        val userId = intent.getStringExtra("userId") // The expected key
            ?: intent.getStringExtra("USER_ID")    // A common alternative
            ?: intent.getStringExtra("uid")        // Another common alternative

        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "User ID is missing. Cannot open profile.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Setup listeners that DON'T need user data
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Load the profile, which will then set up the listeners that DO need user data
        loadUserProfile(userId)

        // Setup the placeholder grid for posts, this doesn't depend on user data
        setupPostsGrid()
    }

    private fun initializeViews() {
        // Initialize views here to avoid repeated findViewById calls
        profileImageView = findViewById(R.id.profile_image) // Assuming this is the ID for the profile ImageView
        usernameTextView = findViewById(R.id.username)
        displayNameTextView = findViewById(R.id.displayName)
        bioTextView = findViewById(R.id.bioText)
        postsCountTextView = findViewById(R.id.postsCount)
        followersCountTextView = findViewById(R.id.followersCount)
        followingCountTextView = findViewById(R.id.followingCount)
    }

    private fun loadUserProfile(userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        populateProfileData(user)
                        // Setup listeners that depend on user data
                        setupUserDependentListeners(user)
                    }
                } else {
                    Toast.makeText(this@view_profile, "User data not found.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@view_profile, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun populateProfileData(user: User) {
        // Set the text data from the 'user' object
        usernameTextView.text = user.username
        displayNameTextView.text = user.fullName
        bioTextView.text = user.bio ?: "No bio available."
        postsCountTextView.text = user.postsCount.toString()
        followersCountTextView.text = user.followersCount.toString()
        followingCountTextView.text = user.followingCount.toString()

        // --- NEW: PROFILE PICTURE LOGIC ---
        // Check if the user has a profile picture URL and it's not empty
        if (!user.profilePictureUrl.isNullOrEmpty()) {
            try {
                // Decode the Base64 string to a Bitmap
                val profileBitmap = decodeBase64(user.profilePictureUrl)
                // Set the bitmap to your ImageView
                profileImageView.setImageBitmap(profileBitmap)
            } catch (e: IllegalArgumentException) {
                // Handle cases where the Base64 string is invalid
                Toast.makeText(this, "Failed to decode profile image.", Toast.LENGTH_SHORT).show()
                profileImageView.setImageResource(R.drawable.default_avatar) // Set a default image on error
            }
        } else {
            // If no profile picture is set, show a default avatar
            profileImageView.setImageResource(R.drawable.default_avatar) // Make sure you have this drawable
        }
    }

    // Utility function to decode a Base64 string into a Bitmap
    private fun decodeBase64(base64: String): Bitmap {
        val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    // This function handles clicks for buttons that need the user object
    private fun setupUserDependentListeners(user: User) {
        findViewById<TextView>(R.id.followingButton).setOnClickListener {
            // This probably should go to a list of followers, not another profile
            // For now, it goes to view_profile_2 as per your original code
            startActivity(Intent(this, view_profile_2::class.java))
        }

        findViewById<View>(R.id.messageButton).setOnClickListener {
            val intent = Intent(this, chat::class.java).apply {
                putExtra("userId", user.uid)
                putExtra("username", user.username) // Passing username to the chat screen
            }
            startActivity(intent)
        }

        // Setup bottom navigation bar
        setupBottomNavigationBar()
    }

    private fun setupPostsGrid() {
        val postsRecyclerView = findViewById<RecyclerView>(R.id.posts_recycler_view)
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        // Use a simple adapter with placeholder data
        val placeholderPosts = List(9) { "Post $it" }
        postsRecyclerView.adapter = PostAdapter(placeholderPosts)
    }

    private fun setupBottomNavigationBar() {
        findViewById<ImageView>(R.id.navHome).setOnClickListener { startActivity(Intent(this, home_page::class.java)) }
        findViewById<ImageView>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, search_feed::class.java)) }
        findViewById<ImageView>(R.id.navCreate).setOnClickListener { startActivity(Intent(this, posting::class.java)) }
        findViewById<ImageView>(R.id.navLike).setOnClickListener { startActivity(Intent(this, following_page::class.java)) }
        findViewById<ImageView>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, my_profile::class.java)) }
    }
}
