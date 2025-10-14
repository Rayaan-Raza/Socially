package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private lateinit var postsRecyclerView: RecyclerView

    // --- NEW: Adapter and list for posts ---
    private lateinit var postsAdapter: ProfilePostGridAdapter
    private val postList = mutableListOf<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)
        enableEdgeToEdge()
        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance()
        initializeViews()

        // Get the user ID from the intent
        val userId = intent.getStringExtra("userId")
            ?: intent.getStringExtra("USER_ID")
            ?: intent.getStringExtra("uid")

        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        // Initialize the grid first with an empty adapter
        setupPostsGrid()

        // Then load the profile and the posts from Firebase
        loadUserProfile(userId)
        loadUserPosts(userId) // NEW: Call function to load posts
    }

    private fun initializeViews() {
        profileImageView = findViewById(R.id.profile_image)
        usernameTextView = findViewById(R.id.username)
        displayNameTextView = findViewById(R.id.displayName)
        bioTextView = findViewById(R.id.bioText)
        postsCountTextView = findViewById(R.id.postsCount)
        followersCountTextView = findViewById(R.id.followersCount)
        followingCountTextView = findViewById(R.id.followingCount)
        postsRecyclerView = findViewById(R.id.posts_recycler_view)
    }

    private fun loadUserProfile(userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.addValueEventListener(object : ValueEventListener { // Use addValueEventListener for real-time updates
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Assuming you have a User data class that matches your Firebase structure
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        populateProfileData(user)
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
        usernameTextView.text = user.username
        displayNameTextView.text = user.fullName
        bioTextView.text = user.bio.takeIf { !it.isNullOrBlank() } ?: "No bio available."

        // Use Glide for loading the profile image, as it's more efficient
        if (!user.profilePictureUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .circleCrop() // Apply a circular mask
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.default_avatar)
        }

        // The counts can be fetched in real-time for better accuracy
        // For now, using the values from the User object is fine.
        postsCountTextView.text = user.postsCount.toString()
        followersCountTextView.text = user.followersCount.toString()
        followingCountTextView.text = user.followingCount.toString()
    }

    // --- NEW: Function to load user's posts ---
    private fun loadUserPosts(userId: String) {
        val postsRef = database.getReference("posts").child(userId)
        postsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postList.clear() // Clear the list to avoid duplicates on update
                for (postSnapshot in snapshot.children) {
                    val post = postSnapshot.getValue(Post::class.java)
                    if (post != null) {
                        postList.add(post)
                    }
                }
                // Sort by newest first and update the adapter
                postList.sortByDescending { it.createdAt }
                postsAdapter.notifyDataSetChanged()

                // Update the post count text to the actual number of posts found
                postsCountTextView.text = postList.size.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@view_profile, "Failed to load posts.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- UPDATED: Setup the grid with the functional adapter ---
    private fun setupPostsGrid() {
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        // Initialize the adapter with the (initially empty) postList
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            // This is where you handle a click on a post in the grid
            // For example, open a new activity to view the post in detail
            Toast.makeText(this, "Clicked on post: ${clickedPost.caption}", Toast.LENGTH_SHORT).show()
            // val intent = Intent(this, PostDetailActivity::class.java)
            // intent.putExtra("postId", clickedPost.postId)
            // intent.putExtra("userId", clickedPost.uid)
            // startActivity(intent)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    private fun setupUserDependentListeners(user: User) {
        findViewById<View>(R.id.messageButton).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", user.uid)
                putExtra("username", user.username)
            }
            startActivity(intent)
        }
        // You might have other buttons like "Follow/Unfollow" here

        setupBottomNavigationBar()
    }

    private fun setupBottomNavigationBar() {
        findViewById<ImageView>(R.id.navHome).setOnClickListener { startActivity(Intent(this, home_page::class.java)) }
        findViewById<ImageView>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, search_feed::class.java)) }
        findViewById<ImageView>(R.id.navCreate).setOnClickListener { startActivity(Intent(this, posting::class.java)) }
        findViewById<ImageView>(R.id.navLike).setOnClickListener { startActivity(Intent(this, following_page::class.java)) }
        findViewById<ImageView>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, my_profile::class.java)) }
    }
}