package com.rayaanraza.i230535

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class my_profile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userRef: DatabaseReference
    private var postsRef: DatabaseReference? = null

    // NEW: top-level refs for live follower/following counts
    private var followersRef: DatabaseReference? = null
    private var followingRef: DatabaseReference? = null

    private var userListener: ValueEventListener? = null
    private var postListener: ValueEventListener? = null
    private var followersCountListener: ValueEventListener? = null
    private var followingCountListener: ValueEventListener? = null

    private lateinit var usernameText: TextView
    private lateinit var displayNameText: TextView
    private lateinit var bioLine1: TextView
    private lateinit var bioLine2: TextView
    private lateinit var postsCountText: TextView
    private lateinit var followersCountText: TextView
    private lateinit var followingCountText: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var editProfileBtn: TextView

    // RecyclerView for posts grid
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
            finish(); return
        }

        userRef = database.getReference("users").child(currentUserId)

        initViews()
        setupClickListeners()
        setupPostsGrid()
        // listeners attached in onStart()
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
        postsRecyclerView = findViewById(R.id.posts_recycler_view)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, edit_profile::class.java))
        }

        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_create).setOnClickListener {
            startActivity(Intent(this, posting::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_like).setOnClickListener {
            startActivity(Intent(this, following_page::class.java)); finish()
        }

        findViewById<FrameLayout>(R.id.friendsHighlightContainer).setOnClickListener {
            startActivity(Intent(this, story::class.java))
        }
        findViewById<FrameLayout>(R.id.profileImage).setOnClickListener {
            startActivity(Intent(this, my_story_view::class.java))
        }

        findViewById<ImageView>(R.id.dropdown_icon).setOnClickListener {
            Toast.makeText(this, "Account options coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            Toast.makeText(this, "Menu coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    // 3-column Instagram-like grid with tight spacing
    private fun setupPostsGrid() {
        val spanCount = 3
        postsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        postsRecyclerView.setHasFixedSize(true)
        postsRecyclerView.addItemDecoration(GridSpacingDecoration(spanCount, dp(1), includeEdge = false))

        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            Toast.makeText(this, "Post: ${clickedPost.caption}", Toast.LENGTH_SHORT).show()
        }
        postsRecyclerView.adapter = postsAdapter
    }

    // Listen to user's own posts under posts/<uid>
    private fun attachPostsListener() {
        postsRef = database.getReference("posts").child(currentUserId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postList.clear()
                for (postSnapshot in snapshot.children) {
                    postSnapshot.getValue(Post::class.java)?.let { postList.add(it) }
                }
                postList.sortByDescending { it.createdAt }
                postsAdapter.notifyDataSetChanged()
                postsCountText.text = postList.size.toString()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@my_profile, "Failed to load posts.", Toast.LENGTH_SHORT).show()
            }
        }
        postsRef?.addValueEventListener(listener)
        postListener = listener
    }

    // Listen to user profile data (name, bio, photo, username)
    private fun attachUserListener() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return

                usernameText.text = snapshot.child("username").getValue(String::class.java) ?: ""

                // Display name: prefer fullName, else first+last
                val fullName = snapshot.child("fullName").getValue(String::class.java)
                val first = snapshot.child("firstName").getValue(String::class.java)
                val last  = snapshot.child("lastName").getValue(String::class.java)
                displayNameText.text = when {
                    !fullName.isNullOrBlank() -> fullName
                    !first.isNullOrBlank() || !last.isNullOrBlank() -> listOfNotNull(first, last).joinToString(" ")
                    else -> ""
                }

                val bio = snapshot.child("bio").getValue(String::class.java) ?: ""
                if (bio.isNotEmpty()) {
                    val bioLines = bio.split("\n", limit = 2)
                    bioLine1.text = bioLines.getOrNull(0) ?: ""
                    bioLine2.text = bioLines.getOrNull(1) ?: ""
                } else {
                    bioLine1.text = ""
                    bioLine2.text = ""
                }

                // NOTE: follower/following counts now come from top-level nodes (see attachCountsListeners)

                // Profile picture: prefer URL; else Base64 (supports data URL prefix)
                val pic = listOf(
                    snapshot.child("profilePictureUrl").getValue(String::class.java),
                    snapshot.child("profileImageUrl").getValue(String::class.java),
                    snapshot.child("profilePic").getValue(String::class.java),
                    snapshot.child("photo").getValue(String::class.java),
                    snapshot.child("profilePhoto").getValue(String::class.java),
                    snapshot.child("profileBase64").getValue(String::class.java),
                    snapshot.child("photoBase64").getValue(String::class.java)
                ).firstOrNull { !it.isNullOrBlank() }

                if (!pic.isNullOrEmpty() && pic.startsWith("http", true)) {
                    Glide.with(this@my_profile)
                        .load(pic)
                        .placeholder(R.drawable.oval)
                        .error(R.drawable.default_avatar)
                        .into(profileImageView)
                } else if (!pic.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val bmp = withContext(Dispatchers.IO) {
                            try {
                                val clean = pic.substringAfter("base64,", pic)
                                val bytes = Base64.decode(clean, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                        }
                        if (bmp != null) {
                            profileImageView.setImageBitmap(bmp)
                        } else {
                            Glide.with(this@my_profile)
                                .load(R.drawable.default_avatar)
                                .placeholder(R.drawable.oval)
                                .into(profileImageView)
                        }
                    }
                } else {
                    Glide.with(this@my_profile)
                        .load(R.drawable.default_avatar)
                        .placeholder(R.drawable.oval)
                        .into(profileImageView)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@my_profile, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        userRef.addValueEventListener(listener)
        userListener = listener
    }

    // NEW: attach live listeners for top-level followers/following
    private fun attachCountsListeners() {
        followersRef = database.getReference("followers").child(currentUserId)
        followingRef = database.getReference("following").child(currentUserId)

        val folL = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                followersCountText.text = s.childrenCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val fingL = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                followingCountText.text = s.childrenCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {}
        }

        followersRef?.addValueEventListener(folL)
        followingRef?.addValueEventListener(fingL)
        followersCountListener = folL
        followingCountListener = fingL
    }

    override fun onResume() {
        super.onResume()
        postsAdapter.notifyDataSetChanged()
    }

    override fun onStart() {
        super.onStart()
        attachUserListener()
        attachPostsListener()
        attachCountsListeners() // NEW: keep counts live from top-level trees
    }

    override fun onStop() {
        super.onStop()
        detachListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        detachListeners()
    }

    private fun detachListeners() {
        userListener?.let { userRef.removeEventListener(it) }
        postListener?.let { postsRef?.removeEventListener(it) }
        followersCountListener?.let { followersRef?.removeEventListener(it) }
        followingCountListener?.let { followingRef?.removeEventListener(it) }
        userListener = null
        postListener = null
        followersCountListener = null
        followingCountListener = null
    }

    // ---- Utilities ----

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    class GridSpacingDecoration(
        private val spanCount: Int,
        private val spacingPx: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: android.view.View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount
            if (includeEdge) {
                outRect.left = spacingPx - column * spacingPx / spanCount
                outRect.right = (column + 1) * spacingPx / spanCount
                if (position < spanCount) outRect.top = spacingPx
                outRect.bottom = spacingPx
            } else {
                outRect.left = column * spacingPx / spanCount
                outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
                if (position >= spanCount) outRect.top = spacingPx
            }
        }
    }
}
