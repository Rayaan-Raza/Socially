package com.group.i230535_i230048

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class view_profile : AppCompatActivity() {

    companion object {
        private const val TAG = "view_profile"
    }

    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var meUid: String = ""

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

    private lateinit var postsAdapter: ProfilePostGridAdapter
    private val postList = mutableListOf<Post>()

    private var targetUid: String = ""
    private var targetUsername: String? = null

    private var isFollowing: Boolean = false
    private var isRequested: Boolean = false

    private var hasLoadedProfile: Boolean = false

    fun loadBottomBarAvatar(navProfile: ImageView) {
        try {
            navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
        } catch (e: Exception) {
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_view_profile)

            dbHelper = AppDbHelper(this)
            queue = Volley.newRequestQueue(this)

            val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

            targetUid = intent.getStringExtra("userId")
                ?: intent.getStringExtra("USER_ID")
                        ?: intent.getStringExtra("uid")
                        ?: ""

            if (targetUid.isEmpty() || meUid.isEmpty()) {
                Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (targetUid == meUid) {
                startActivity(Intent(this, my_profile::class.java))
                finish()
                return
            }

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

            // Start with a loading state
            showLoadingState()

        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // ======================================================================
    // CHANGED: Logic updated to prioritize API calls over Database
    // ======================================================================
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Checking network status...")

        if (isNetworkAvailable(this)) {
            // Online: Fetch from API directly (Live Data First)
            Log.d(TAG, "onStart: Online. Fetching live data...")
            fetchUserProfileFromApi()
            fetchUserPostsFromApi()
        } else {
            // Offline: Fallback to Database
            Log.d(TAG, "onStart: Offline. Loading from Database...")
            val hasLocalData = loadUserProfileFromDb()
            if (hasLocalData) {
                loadUserPostsFromDb()
            } else {
                Toast.makeText(this, "No internet and no cached data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchUserProfileFromApi() {
        val url = "${AppGlobals.BASE_URL}user_profile_get.php?uid=$meUid&targetUid=$targetUid"
        Log.d(TAG, "fetchUserProfileFromApi: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val userObj = dataObj.getJSONObject("user")
                        val relationshipObj = dataObj.getJSONObject("relationship")

                        // 1. Extract Data
                        val uid = userObj.getString("uid")
                        val username = userObj.getString("username")
                        val fullName = userObj.getString("fullName")
                        val bio = userObj.optString("bio", "")
                        val profilePictureUrl = userObj.optString("profilePictureUrl", "")
                        val photo = userObj.optString("photo", "")
                        val postsCount = userObj.getInt("postsCount")
                        val followersCount = userObj.getInt("followersCount")
                        val followingCount = userObj.getInt("followingCount")

                        // 2. Update UI IMMEDIATELY (Live Data)
                        runOnUiThread {
                            targetUsername = username
                            usernameTextView.text = username
                            displayNameTextView.text = fullName.ifBlank { username }
                            bioTextView.text = bio.ifBlank { "No bio available." }
                            postsCountTextView.text = postsCount.toString()
                            followersCountTextView.text = followersCount.toString()
                            followingCountTextView.text = followingCount.toString()

                            try {
                                profileImageView.loadUserAvatar(uid, meUid, R.drawable.default_avatar)
                            } catch (e: Exception) {
                                profileImageView.setImageResource(R.drawable.default_avatar)
                            }

                            isFollowing = relationshipObj.getBoolean("isFollowing")
                            isRequested = relationshipObj.getBoolean("isRequested")
                            setupFollowButton(isFollowing, isRequested)

                            messageButton.isEnabled = true
                            messageButton.alpha = 1.0f
                            hasLoadedProfile = true
                        }

                        // 3. Save to Database (Cache for later/offline)
                        Thread {
                            try {
                                val cv = ContentValues().apply {
                                    put(DB.User.COLUMN_UID, uid)
                                    put(DB.User.COLUMN_USERNAME, username)
                                    put(DB.User.COLUMN_FULL_NAME, fullName)
                                    put(DB.User.COLUMN_PROFILE_PIC_URL, profilePictureUrl)
                                    put(DB.User.COLUMN_PHOTO, photo)
                                    put(DB.User.COLUMN_BIO, bio)
                                }
                                dbHelper.writableDatabase.insertWithOnConflict(
                                    DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE
                                )
                                Log.d(TAG, "fetchUserProfileFromApi: Data cached to DB")
                            } catch (e: Exception) {
                                Log.e(TAG, "fetchUserProfileFromApi: DB Save Error", e)
                            }
                        }.start()

                    } else {
                        // API Failure logic
                        Log.e(TAG, "fetchUserProfileFromApi: API Error: ${json.optString("message")}")
                        loadUserProfileFromDb() // Fallback
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchUserProfileFromApi: Parse Error", e)
                    loadUserProfileFromDb() // Fallback
                }
            },
            { error ->
                Log.e(TAG, "fetchUserProfileFromApi: Network Error", error)
                loadUserProfileFromDb() // Fallback
            }
        )
        queue.add(stringRequest)
    }

    private fun fetchUserPostsFromApi() {
        val url = "${AppGlobals.BASE_URL}profile_posts_get.php?targetUid=$targetUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)

                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val tempPostList = mutableListOf<Post>()

                        // 1. Parse JSON into List
                        for (i in 0 until dataArray.length()) {
                            val postObj = dataArray.getJSONObject(i)
                            tempPostList.add(
                                Post(
                                    postId = postObj.getString("postId"),
                                    uid = postObj.getString("uid"),
                                    username = postObj.optString("username", ""),
                                    caption = postObj.optString("caption", ""),
                                    imageUrl = postObj.optString("imageUrl", ""),
                                    imageBase64 = postObj.optString("imageBase64", ""),
                                    createdAt = postObj.getLong("createdAt"),
                                    likeCount = postObj.optLong("likeCount", 0),
                                    commentCount = postObj.optLong("commentCount", 0)
                                )
                            )
                        }

                        // 2. Update UI IMMEDIATELY
                        runOnUiThread {
                            postList.clear()
                            postList.addAll(tempPostList)
                            postsAdapter.notifyDataSetChanged()
                            postsCountTextView.text = postList.size.toString()
                        }

                        // 3. Update Database (Cache)
                        Thread {
                            val db = dbHelper.writableDatabase
                            db.beginTransaction()
                            try {
                                // Clear old posts for this user
                                db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid))

                                // Insert new live posts
                                for (post in tempPostList) {
                                    val cv = ContentValues().apply {
                                        put(DB.Post.COLUMN_POST_ID, post.postId)
                                        put(DB.Post.COLUMN_UID, post.uid)
                                        put(DB.Post.COLUMN_USERNAME, post.username)
                                        put(DB.Post.COLUMN_CAPTION, post.caption)
                                        put(DB.Post.COLUMN_IMAGE_URL, post.imageUrl)
                                        put(DB.Post.COLUMN_IMAGE_BASE64, post.imageBase64)
                                        put(DB.Post.COLUMN_CREATED_AT, post.createdAt)
                                        put(DB.Post.COLUMN_LIKE_COUNT, post.likeCount)
                                        put(DB.Post.COLUMN_COMMENT_COUNT, post.commentCount)
                                    }
                                    db.insert(DB.Post.TABLE_NAME, null, cv)
                                }
                                db.setTransactionSuccessful()
                                Log.d(TAG, "fetchUserPostsFromApi: Posts cached to DB")
                            } catch (e: Exception) {
                                Log.e(TAG, "fetchUserPostsFromApi: DB Save Error", e)
                            } finally {
                                db.endTransaction()
                            }
                        }.start()

                    } else {
                        loadUserPostsFromDb() // Fallback
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchUserPostsFromApi: Error", e)
                    loadUserPostsFromDb() // Fallback
                }
            },
            { error ->
                Log.e(TAG, "fetchUserPostsFromApi: Network Error", error)
                loadUserPostsFromDb() // Fallback
            }
        )
        queue.add(stringRequest)
    }

    // ======================================================================
    // Existing Methods (Unchanged logic, just helper functions)
    // ======================================================================

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()
        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Uncaught")) {
            return "{\"success\": false, \"message\": \"Server error\"}"
        }
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            val jsonObjectStart = cleaned.indexOf("{\"")
            val jsonArrayStart = cleaned.indexOf("[{")
            val jsonStart = when {
                jsonObjectStart == -1 && jsonArrayStart == -1 -> -1
                jsonObjectStart == -1 -> jsonArrayStart
                jsonArrayStart == -1 -> jsonObjectStart
                else -> minOf(jsonObjectStart, jsonArrayStart)
            }
            if (jsonStart > 0) cleaned = cleaned.substring(jsonStart)
            else if (jsonStart == -1) return "{\"success\": false, \"message\": \"Invalid server response\"}"
        }
        val lastBrace = cleaned.lastIndexOf('}')
        val lastBracket = cleaned.lastIndexOf(']')
        val jsonEnd = maxOf(lastBrace, lastBracket)
        if (jsonEnd > 0 && jsonEnd < cleaned.length - 1) {
            cleaned = cleaned.substring(0, jsonEnd + 1)
        }
        return cleaned
    }

    private fun showLoadingState() {
        try {
            usernameTextView.text = "Loading..."
            displayNameTextView.text = "Loading..."
            bioTextView.text = ""
            postsCountTextView.text = "..."
            followersCountTextView.text = "..."
            followingCountTextView.text = "..."
            followButton.isEnabled = false
            followButton.alpha = 0.5f
        } catch (e: Exception) {
            Log.e(TAG, "showLoadingState: Error", e)
        }
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

    private fun loadUserProfileFromDb(): Boolean {
        Log.d(TAG, "loadUserProfileFromDb: Reading from cache...")
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.User.TABLE_NAME, null,
                "${DB.User.COLUMN_UID} = ?", arrayOf(targetUid),
                null, null, null
            )

            var hasData = false
            if (cursor.moveToFirst()) {
                try {
                    val user = User(
                        uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_UID)),
                        username = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_USERNAME)) ?: "",
                        fullName = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_FULL_NAME)) ?: "",
                        bio = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_BIO)) ?: "",
                        profilePictureUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PROFILE_PIC_URL)) ?: ""
                    )
                    populateProfileData(user)
                    hasData = true
                } catch (e: Exception) {
                    Log.e(TAG, "loadUserProfileFromDb: Error reading user", e)
                }
            }
            cursor.close()
            return hasData
        } catch (e: Exception) {
            return false
        }
    }

    private fun populateProfileData(user: User) {
        runOnUiThread {
            targetUsername = user.username
            usernameTextView.text = user.username
            displayNameTextView.text = user.fullName.takeIf { it.isNotBlank() } ?: user.username
            bioTextView.text = user.bio.takeIf { it.isNotBlank() } ?: "No bio available."
            try {
                profileImageView.loadUserAvatar(user.uid, meUid, R.drawable.default_avatar)
            } catch (e: Exception) {
                profileImageView.setImageResource(R.drawable.default_avatar)
            }
        }
    }

    private fun loadUserPostsFromDb() {
        Log.d(TAG, "loadUserPostsFromDb: Reading posts from cache...")
        try {
            postList.clear()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.Post.TABLE_NAME, null,
                "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid),
                null, null, "${DB.Post.COLUMN_CREATED_AT} DESC"
            )

            while (cursor.moveToNext()) {
                try {
                    postList.add(
                        Post(
                            postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                            uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                            username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)) ?: "",
                            caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)) ?: "",
                            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)) ?: "",
                            imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)) ?: "",
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                            likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                            commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "loadUserPostsFromDb: Error parsing post", e)
                }
            }
            cursor.close()
            postsAdapter.notifyDataSetChanged()
            postsCountTextView.text = postList.size.toString()
        } catch (e: Exception) {
            Log.e(TAG, "loadUserPostsFromDb: Exception", e)
        }
    }

    private fun setupPostsGrid() {
        val spanCount = 3
        postsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        postsRecyclerView.setHasFixedSize(true)
        postsRecyclerView.addItemDecoration(
            my_profile.GridSpacingDecoration(spanCount, dp(1), includeEdge = false)
        )
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            openPostDetail(clickedPost)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    private fun openPostDetail(post: Post) {
        try {
            val intent = Intent(this, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", post.postId)
                putExtra("USER_ID", post.uid)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMessageButton() {
        messageButton.setOnClickListener { createChatAndOpen() }
    }

    private fun createChatAndOpen() {
        if (!isNetworkAvailable(this)) {
            openChatActivity()
            return
        }

        val url = "${AppGlobals.BASE_URL}chat_create.php"
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                openChatActivity()
            },
            { error ->
                openChatActivity()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("uid1" to meUid, "uid2" to targetUid)
            }
        }
        queue.add(stringRequest)
    }

    private fun openChatActivity() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("userId", targetUid)
            putExtra("username", targetUsername)
        }
        startActivity(intent)
    }

    private fun setupFollowButton(following: Boolean, requested: Boolean) {
        followButton.visibility = View.VISIBLE
        followButton.isEnabled = true
        followButton.alpha = 1.0f
        setFollowState(following, requested)

        followButton.setOnClickListener {
            if (isFollowing || isRequested) unfollowUser() else followUser()
        }
    }

    private fun setFollowState(following: Boolean, requested: Boolean) {
        isFollowing = following
        isRequested = requested
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

    private fun followUser() {
        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "${AppGlobals.BASE_URL}follow_user.php"
        followButton.isEnabled = false
        followButton.alpha = 0.5f

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        val status = json.getJSONObject("data").getString("status")
                        runOnUiThread {
                            when (status) {
                                "following" -> {
                                    setFollowState(true, false)
                                    Toast.makeText(this, "Now following", Toast.LENGTH_SHORT).show()
                                }
                                "requested", "request_sent" -> {
                                    setFollowState(false, true)
                                    Toast.makeText(this, "Follow request sent", Toast.LENGTH_SHORT).show()
                                }
                            }
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }
                        fetchUserProfileFromApi()
                    } else {
                        Toast.makeText(this, json.optString("message"), Toast.LENGTH_SHORT).show()
                        followButton.isEnabled = true
                        followButton.alpha = 1.0f
                    }
                } catch (e: Exception) {
                    followButton.isEnabled = true
                    followButton.alpha = 1.0f
                }
            },
            {
                followButton.isEnabled = true
                followButton.alpha = 1.0f
            }
        ) {
            override fun getParams(): MutableMap<String, String> = hashMapOf("uid" to meUid, "targetUid" to targetUid)
        }
        queue.add(stringRequest)
    }

    private fun unfollowUser() {
        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }
        val url = "${AppGlobals.BASE_URL}unfollow_user.php"
        followButton.isEnabled = false
        followButton.alpha = 0.5f

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        runOnUiThread {
                            setFollowState(false, false)
                            Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show()
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }
                        fetchUserProfileFromApi()
                    } else {
                        followButton.isEnabled = true
                        followButton.alpha = 1.0f
                    }
                } catch (e: Exception) {
                    followButton.isEnabled = true
                    followButton.alpha = 1.0f
                }
            },
            {
                followButton.isEnabled = true
                followButton.alpha = 1.0f
            }
        ) {
            override fun getParams(): MutableMap<String, String> = hashMapOf("uid" to meUid, "targetUid" to targetUid)
        }
        queue.add(stringRequest)
    }

    private fun setupBottomNavigationBar() { }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}