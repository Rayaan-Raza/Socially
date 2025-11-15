package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.ContentValues // CHANGED
import android.content.Context // CHANGED
import android.content.Intent
import android.database.sqlite.SQLiteDatabase // CHANGED
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Base64
import android.util.Log // CHANGED
import android.view.Gravity
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
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
import com.bumptech.glide.Glide
// REMOVED: All Firebase imports
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import com.group.i230535_i230048.AppDbHelper // CHANGED
import com.group.i230535_i230048.DB // CHANGED
import org.json.JSONObject // CHANGED

class view_profile : AppCompatActivity() {

    // --- CHANGED: Swapped to Volley/DB/Session ---
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var meUid: String = ""
    // ---

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

    private var targetUid: String = ""
    private var targetUsername: String? = null

    // CHANGED: Simplified follow state
    private var isFollowing: Boolean = false

    // REMOVED: Firebase Listeners and Refs

    // CHANGED: Migrated to load from local DB
    fun loadBottomBarAvatar(navProfile: ImageView) {
        navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)

        // --- CHANGED: Setup DB, Volley, and Session ---
        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        // ---

        targetUid = intent.getStringExtra("userId")
            ?: intent.getStringExtra("USER_ID")
                    ?: intent.getStringExtra("uid")
                    ?: ""

        if (targetUid.isEmpty() || meUid.isEmpty()) {
            Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        // --- CHANGED: Redirect to my_profile if viewing self ---
        if (targetUid == meUid) {
            startActivity(Intent(this, my_profile::class.java))
            finish()
            return
        }
        // ---

        val navProfile = findViewById<ImageView>(R.id.navProfile)
        loadBottomBarAvatar(navProfile)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupPostsGrid()
        setupMessageButton()
        setupBottomNavigationBar()
    }

    // --- CHANGED: Load data in onStart ---
    override fun onStart() {
        super.onStart()
        // 1. Load data from local DB instantly
        loadUserProfileFromDb()
        loadUserPostsFromDb()
        // 2. Fetch fresh data from network
        fetchUserProfileFromApi()
        fetchUserPostsFromApi()
    }

    override fun onDestroy() {
        super.onDestroy()
        // REMOVED: Firebase listeners
    }

    private fun initializeViews() {
        // (No changes)
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

    // --- NEW: Load user from local DB ---
    private fun loadUserProfileFromDb() {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.User.TABLE_NAME, null,
            "${DB.User.COLUMN_UID} = ?", arrayOf(targetUid),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            val user = User(
                uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_UID)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_USERNAME)),
                fullName = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_FULL_NAME)),
                bio = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_BIO)),
                profilePictureUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PROFILE_PIC_URL))
            )
            populateProfileData(user)
        }
        cursor.close()
    }

    // --- NEW: Fetch user from API ---
    private fun fetchUserProfileFromApi() {
        // Dev A needs to ensure this API returns "isFollowing: true/false"
        val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$targetUid&my_uid=$meUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")

                        // 1. Save to DB
                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.getString("uid"))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.getString("username"))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.getString("fullName"))
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, userObj.getString("profilePictureUrl"))
                        cv.put(DB.User.COLUMN_BIO, userObj.getString("bio"))
                        dbHelper.writableDatabase.insertWithOnConflict(
                            DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        // 2. Reload data from DB
                        loadUserProfileFromDb()

                        // 3. Update counts
                        postsCountTextView.text = userObj.getInt("postsCount").toString()
                        followersCountTextView.text = userObj.getInt("followersCount").toString()
                        followingCountTextView.text = userObj.getInt("followingCount").toString()

                        // 4. Update follow state
                        // TODO: Dev A must add 'isFollowing' and 'isRequested' to this API response
                        isFollowing = userObj.optBoolean("isFollowing", false)
                        val isRequested = userObj.optBoolean("isRequested", false)
                        setupFollowButton(isFollowing, isRequested)

                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing profile: ${e.message}") }
            },
            { error -> Log.w("view_profile", "Volley error fetching profile: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun populateProfileData(user: User) {
        targetUsername = user.username
        usernameTextView.text = user.username
        displayNameTextView.text = user.fullName
        bioTextView.text = user.bio.takeIf { it.isNotBlank() } ?: "No bio available."
        profileImageView.loadUserAvatar(user.uid, meUid, R.drawable.default_avatar)
    }

    // --- NEW: Load posts from local DB ---
    private fun loadUserPostsFromDb() {
        postList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.Post.TABLE_NAME, null,
            "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid),
            null, null, DB.Post.COLUMN_CREATED_AT + " DESC"
        )

        while (cursor.moveToNext()) {
            postList.add(
                Post(
                    postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                    uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                    imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL))
                    // ... add other fields as needed
                )
            )
        }
        cursor.close()
        postsAdapter.notifyDataSetChanged()
        postsCountTextView.text = postList.size.toString()
    }

    // --- NEW: Fetch posts from API ---
    private fun fetchUserPostsFromApi() {
        val url = AppGlobals.BASE_URL + "get_user_posts.php?uid=$targetUid" // Assumed endpoint
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val listType = object : TypeToken<List<Post>>() {}.type
                        val newPosts: List<Post> = Gson().fromJson(dataArray.toString(), listType)

                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid))
                            for (post in newPosts) {
                                val cv = ContentValues()
                                cv.put(DB.Post.COLUMN_POST_ID, post.postId)
                                cv.put(DB.Post.COLUMN_UID, post.uid)
                                cv.put(DB.Post.COLUMN_IMAGE_URL, post.imageUrl)
                                // ... save other fields
                                db.insert(DB.Post.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        loadUserPostsFromDb() // Reload from DB
                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing posts: ${e.message}") }
            },
            { error -> Log.w("view_profile", "Volley error fetching posts: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun openPostDetail(post: Post) {
        // (No changes)
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
        }
        startActivity(intent)
    }

    private fun setupPostsGrid() {
        // (No changes)
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            openPostDetail(clickedPost)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    private fun setupMessageButton() {
        // (No changes)
        messageButton.visibility = View.VISIBLE
        messageButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", targetUid)
                putExtra("username", targetUsername ?: "User")
            }
            startActivity(intent)
        }
    }

    // --- CHANGED: Migrated Follow logic ---
    private fun setupFollowButton(isFollowing: Boolean, isRequested: Boolean) {
        followButton.visibility = View.VISIBLE

        // 1. Set initial state from API
        setFollowState(isFollowing, isRequested)

        // 2. Set click listener
        followButton.setOnClickListener {
            if (this.isFollowing) {
                unfollowUser() // User wants to unfollow
            } else {
                followUser() // User wants to follow (or cancel request)
            }
        }
    }

    // --- CHANGED: Renamed and simplified ---
    private fun setFollowState(following: Boolean, requested: Boolean) {
        this.isFollowing = following
        followButton.gravity = Gravity.CENTER
        followButton.setPadding(24, 12, 24, 12)

        when {
            following -> {
                followButton.text = "Following"
                followButton.setBackgroundResource(R.drawable.follow_button)
                ViewCompat.setBackgroundTintList(followButton, null)
                followButton.setTextColor(getColor(R.color.black))
            }
            requested -> {
                followButton.text = "Requested"
                followButton.setBackgroundResource(R.drawable.requested_button)
                ViewCompat.setBackgroundTintList(followButton, null)
                followButton.setTextColor(getColor(R.color.gray))
            }
            else -> {
                followButton.text = "Follow"
                followButton.setBackgroundResource(R.drawable.message_bttn)
                ViewCompat.setBackgroundTintList(followButton, ColorStateList.valueOf(getColor(R.color.smd_theme)))
                followButton.setTextColor(getColor(android.R.color.white))
            }
        }
    }

    // --- NEW: API call for following ---
    private fun followUser() {
        // This function will now handle BOTH follow and "cancel request"
        // The API doc `ApiService.kt` has `follow.php`
        val url = AppGlobals.BASE_URL + "follow.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        // API should return the new state
                        val newIsFollowing = json.optBoolean("isFollowing", false)
                        val newIsRequested = json.optBoolean("isRequested", false)
                        setFollowState(newIsFollowing, newIsRequested)
                        fetchUserProfileFromApi() // Refresh counts
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing follow: ${e.message}") }
            },
            { error -> Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["follower_id"] = meUid
                params["following_id"] = targetUid
                return params
            }
        }
        queue.add(stringRequest)
    }

    // --- NEW: API call for unfollowing ---
    private fun unfollowUser() {
        val url = AppGlobals.BASE_URL + "unfollow.php" // (from ApiService.kt)

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        setFollowState(false, false) // Update UI immediately
                        fetchUserProfileFromApi() // Refresh counts
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing unfollow: ${e.message}") }
            },
            { error -> Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["follower_id"] = meUid
                params["following_id"] = targetUid
                return params
            }
        }
        queue.add(stringRequest)
    }

    // REMOVED: observeCounts (now part of fetchUserProfileFromApi)
    // REMOVED: attachStateListeners, applyCombinedState, sendFollowRequest, cancelRequest, follow, unfollow

    private fun setupBottomNavigationBar() {
        // (No changes)
    }
}