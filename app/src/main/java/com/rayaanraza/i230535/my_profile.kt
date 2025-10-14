package com.rayaanraza.i230535

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class my_profile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference

    private lateinit var usernameText: TextView
    private lateinit var displayNameText: TextView
    private lateinit var bioLine1: TextView
    private lateinit var bioLine2: TextView
    private lateinit var postsCountText: TextView
    private lateinit var followersCountText: TextView
    private lateinit var followingCountText: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var editProfileBtn: TextView

    // NEW: RecyclerView for posts grid
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var postsAdapter: ProfilePostGridAdapter
    private val postList = mutableListOf<Post>()

    private var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_profile)
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

        initViews()
        setupClickListeners()
        setupPostsGrid() // NEW: Setup posts grid
        loadUserData()
        loadMyPosts() // NEW: Load user's posts
    }

    private fun initViews() {
        usernameText = findViewById(R.id.username)
        displayNameText = findViewById(R.id.displayName)
        bioLine1 = findViewById(R.id.bioLine1)
        bioLine2 = findViewById(R.id.bioLine2)
        postsCountText = findViewById(R.id.postsCount)
        followersCountText = findViewById(R.id.followersCount)
        followingCountText = findViewById(R.id.followingCount)
        editProfileBtn = findViewById(R.id.editProfileBtn)
        profileImageView = findViewById(R.id.designHighlightIcon)

        // NEW: Initialize RecyclerView for posts
        postsRecyclerView = findViewById(R.id.posts_recycler_view)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, edit_profile::class.java))
        }

        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.nav_create).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.nav_like).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()
        }

        findViewById<FrameLayout>(R.id.friendsHighlightContainer).setOnClickListener {
            startActivity(Intent(this, story::class.java))
        }

        findViewById<FrameLayout>(R.id.profileImage).setOnClickListener {
            startActivity(Intent(this, my_story_view::class.java))
        }

        // Other listeners
        findViewById<ImageView>(R.id.dropdown_icon).setOnClickListener {
            Toast.makeText(this, "Account options coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            Toast.makeText(this, "Menu coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    // NEW: Setup the posts grid layout
    private fun setupPostsGrid() {
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3) // 3 columns
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            // Handle post click - open post detail or show full view
            Toast.makeText(this, "Post: ${clickedPost.caption}", Toast.LENGTH_SHORT).show()
            // You can navigate to a post detail activity here if you have one
            // val intent = Intent(this, PostDetailActivity::class.java)
            // intent.putExtra("postId", clickedPost.postId)
            // intent.putExtra("userId", clickedPost.uid)
            // startActivity(intent)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    // NEW: Load user's own posts
    private fun loadMyPosts() {
        val postsRef = database.getReference("posts").child(currentUserId)
        postsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postList.clear()
                for (postSnapshot in snapshot.children) {
                    val post = postSnapshot.getValue(Post::class.java)
                    if (post != null) {
                        postList.add(post)
                    }
                }
                // Sort by newest first
                postList.sortByDescending { it.createdAt }
                postsAdapter.notifyDataSetChanged()

                // Update the actual post count
                postsCountText.text = postList.size.toString()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@my_profile, "Failed to load posts.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadUserData() {
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Username
                    usernameText.text = snapshot.child("username").getValue(String::class.java) ?: ""

                    // Display name
                    displayNameText.text = snapshot.child("fullName").getValue(String::class.java) ?: ""

                    // Bio
                    val bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                    if (bio.isNotEmpty()) {
                        val bioLines = bio.split("\n", limit = 2)
                        bioLine1.text = bioLines.getOrNull(0) ?: ""
                        bioLine2.text = bioLines.getOrNull(1) ?: ""
                    } else {
                        bioLine1.text = ""
                        bioLine2.text = ""
                    }

                    // Followers and Following counts
                    followersCountText.text = (snapshot.child("followersCount").getValue(Int::class.java) ?: 0).toString()
                    followingCountText.text = (snapshot.child("followingCount").getValue(Int::class.java) ?: 0).toString()

                    // Posts count will be updated from loadMyPosts()

                    // Profile picture
                    val profilePic = snapshot.child("profilePictureUrl").getValue(String::class.java)
                        ?: snapshot.child("photo").getValue(String::class.java)

                    if (!profilePic.isNullOrEmpty()) {
                        try {
                            val bitmap = decodeBase64(profilePic)
                            profileImageView.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            // Keep default image if decoding fails
                            profileImageView.setImageResource(R.drawable.default_avatar)
                        }
                    } else {
                        profileImageView.setImageResource(R.drawable.default_avatar)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@my_profile, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun decodeBase64(base64: String): Bitmap {
        val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    override fun onResume() {
        super.onResume()
        if (currentUserId.isNotEmpty()) {
            loadUserData()
            loadMyPosts()
        }
    }
}