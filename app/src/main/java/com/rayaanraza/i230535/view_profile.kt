package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.util.Base64
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class view_profile : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private val rtdb: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val meUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    // Views
    private lateinit var profileImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var displayNameTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var postsCountTextView: TextView
    private lateinit var followersCountTextView: TextView
    private lateinit var followingCountTextView: TextView
    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var followButton: TextView
    private lateinit var messageButton: TextView

    // Posts
    private lateinit var postsAdapter: ProfilePostGridAdapter
    private val postList = mutableListOf<Post>()

    private var targetUid: String? = null
    private var targetUsername: String? = null

    fun loadBottomBarAvatar(navProfile: ImageView) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("profilePictureUrl")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b64 = snapshot.getValue(String::class.java) ?: return

                val clean = b64.substringAfter(",", b64)  // remove data:image/... prefix
                val bytes = try {
                    Base64.decode(clean, Base64.DEFAULT)
                } catch (_: Exception) { null } ?: return

                Glide.with(navProfile.context)
                    .asBitmap()
                    .load(bytes)
                    .placeholder(R.drawable.oval)
                    .error(R.drawable.oval)
                    .circleCrop()
                    .into(navProfile)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)
        val navProfile = findViewById<ImageView>(R.id.navProfile)
        loadBottomBarAvatar(navProfile)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance()
        initializeViews()

        targetUid = intent.getStringExtra("userId")
            ?: intent.getStringExtra("USER_ID")
                    ?: intent.getStringExtra("uid")

        if (targetUid.isNullOrEmpty()) {
            Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupPostsGrid()
        loadUserProfile(targetUid!!)
        loadUserPosts(targetUid!!)
        setupFollowSection(targetUid!!)
        setupMessageButton(targetUid!!)
        setupBottomNavigationBar()
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
        followButton = findViewById(R.id.followingButton)
        messageButton = findViewById(R.id.messageButton)
    }

    private fun loadUserProfile(userId: String) {
        val userRef = database.getReference("users").child(userId)
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@view_profile, "User data not found.", Toast.LENGTH_SHORT).show()
                    return
                }
                val user = snapshot.getValue(User::class.java) ?: return
                targetUsername = user.username
                populateProfileData(user)
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

        if (!user.profilePictureUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.default_avatar)
                .error(R.drawable.default_avatar)
                .circleCrop()
                .into(profileImageView)
        } else {
            profileImageView.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun loadUserPosts(userId: String) {
        val postsRef = database.getReference("posts").child(userId)
        postsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                postList.clear()
                for (postSnapshot in snapshot.children) {
                    postSnapshot.getValue(Post::class.java)?.let { postList.add(it) }
                }
                postList.sortByDescending { it.createdAt }
                postsAdapter.notifyDataSetChanged()
                postsCountTextView.text = postList.size.toString()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@view_profile, "Failed to load posts.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupPostsGrid() {
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            Toast.makeText(this, "Clicked on post: ${clickedPost.caption}", Toast.LENGTH_SHORT).show()
        }
        postsRecyclerView.adapter = postsAdapter
    }

    // ---------------- MESSAGE BUTTON ----------------

    private fun setupMessageButton(targetUid: String) {
        val me = meUid
        if (me == null || me == targetUid) {
            // Hide message button if not logged in or viewing own profile
            messageButton.visibility = View.GONE
            return
        }

        messageButton.visibility = View.VISIBLE

        messageButton.setOnClickListener {
            // Open ChatActivity with this user
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", targetUid)
                putExtra("username", targetUsername ?: "User")
            }
            startActivity(intent)
        }
    }

    // ---------------- FOLLOW / UNFOLLOW ----------------

    private fun setupFollowSection(targetUid: String) {
        val me = meUid
        if (me == null || me == targetUid) {
            // hide when not logged in or viewing own profile
            followButton.visibility = View.GONE
            return
        }

        followButton.visibility = View.VISIBLE

        // Reflect current state live: /following/<me>/<target>
        rtdb.child("following").child(me).child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val isFollowing = s.getValue(Boolean::class.java) == true
                    applyFollowStyle(isFollowing)
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Live counts from top-level trees
        observeCounts(targetUid)

        followButton.setOnClickListener {
            // Flip state by reading once
            rtdb.child("following").child(me).child(targetUid)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val currentlyFollowing = s.getValue(Boolean::class.java) == true
                        if (currentlyFollowing) {
                            unfollow(me, targetUid)
                        } else {
                            follow(me, targetUid)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun follow(me: String, target: String) {
        if (me == target) return
        val updates = hashMapOf<String, Any?>(
            "/following/$me/$target" to true,
            "/followers/$target/$me" to true
        )
        rtdb.updateChildren(updates).addOnFailureListener {
            Toast.makeText(this, "Follow failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unfollow(me: String, target: String) {
        if (me == target) return
        val updates = hashMapOf<String, Any?>(
            "/following/$me/$target" to null,
            "/followers/$target/$me" to null
        )
        rtdb.updateChildren(updates).addOnFailureListener {
            Toast.makeText(this, "Unfollow failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeCounts(targetUid: String) {
        // Followers count = children under /followers/<target>
        rtdb.child("followers").child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    followersCountTextView.text = s.childrenCount.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        // Following count = children under /following/<target>
        rtdb.child("following").child(targetUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    followingCountTextView.text = s.childrenCount.toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun applyFollowStyle(isFollowing: Boolean) {
        if (isFollowing) {
            followButton.text = "Following"
            followButton.setBackgroundResource(R.drawable.follow_button) // white/outlined
            followButton.setTextColor(getColor(R.color.black))
        } else {
            followButton.text = "Follow"
            followButton.setBackgroundResource(R.drawable.message_bttn)  // blue filled
            followButton.setTextColor(getColor(R.color.white))
        }
    }

    // ---------------- NAV ----------------

    private fun setupBottomNavigationBar() {
        findViewById<ImageView>(R.id.navHome).setOnClickListener { startActivity(Intent(this, home_page::class.java)) }
        findViewById<ImageView>(R.id.navSearch).setOnClickListener { startActivity(Intent(this, search_feed::class.java)) }
        findViewById<ImageView>(R.id.navCreate).setOnClickListener { startActivity(Intent(this, posting::class.java)) }
        findViewById<ImageView>(R.id.navLike).setOnClickListener { startActivity(Intent(this, following_page::class.java)) }
        findViewById<ImageView>(R.id.navProfile).setOnClickListener { startActivity(Intent(this, my_profile::class.java)) }
    }
}