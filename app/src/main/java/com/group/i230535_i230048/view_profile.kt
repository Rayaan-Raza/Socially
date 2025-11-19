package com.group.i230535_i230048

import android.annotation.SuppressLint
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
        Log.d(TAG, "loadBottomBarAvatar: meUid=$meUid")
        try {
            navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
        } catch (e: Exception) {
            Log.e(TAG, "loadBottomBarAvatar: Error", e)
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting view_profile")

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

            Log.d(TAG, "onCreate: Target UID=$targetUid, My UID=$meUid")

            if (targetUid.isEmpty() || meUid.isEmpty()) {
                Log.e(TAG, "onCreate: Missing user IDs")
                Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (targetUid == meUid) {
                Log.d(TAG, "onCreate: Viewing own profile, redirecting to my_profile")
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
            findViewById<ImageView>(R.id.backButton).setOnClickListener {
                Log.d(TAG, "Back button clicked")
                finish()
            }

            setupPostsGrid()
            setupMessageButton()
            setupBottomNavigationBar()

            showLoadingState()
            Log.d(TAG, "onCreate: Initialization complete")

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Exception", e)
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Loading data")

        val hasLocalData = loadUserProfileFromDb()

        fetchUserProfileFromApi()
        fetchUserPostsFromApi()

        if (!hasLocalData) {
            Log.d(TAG, "onStart: No local data found, waiting for API response")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        Log.d(TAG, "cleanJsonResponse: Raw response (first 500 chars): ${cleaned.take(500)}")

        // Check if this is a pure PHP error
        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Uncaught")) {
            Log.e(TAG, "cleanJsonResponse: PHP Fatal error detected!")

            val errorMessage = if (cleaned.contains("Unknown column")) {
                "Database column mismatch"
            } else {
                "PHP server error"
            }

            Log.e(TAG, "cleanJsonResponse: $errorMessage")
            return "{\"success\": false, \"message\": \"$errorMessage\"}"
        }

        // Check if response starts with JSON
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            Log.w(TAG, "cleanJsonResponse: Response doesn't start with JSON! First 50 chars: ${cleaned.take(50)}")

            val jsonObjectStart = cleaned.indexOf("{\"")
            val jsonArrayStart = cleaned.indexOf("[{")

            val jsonStart = when {
                jsonObjectStart == -1 && jsonArrayStart == -1 -> -1
                jsonObjectStart == -1 -> jsonArrayStart
                jsonArrayStart == -1 -> jsonObjectStart
                else -> minOf(jsonObjectStart, jsonArrayStart)
            }

            if (jsonStart > 0) {
                val beforeJson = cleaned.substring(0, jsonStart)
                Log.e(TAG, "cleanJsonResponse: Content before JSON: $beforeJson")
                cleaned = cleaned.substring(jsonStart)
                Log.d(TAG, "cleanJsonResponse: Cleaned JSON starts with: ${cleaned.take(100)}")
            } else if (jsonStart == -1) {
                Log.e(TAG, "cleanJsonResponse: No JSON found in response!")
                return "{\"success\": false, \"message\": \"Invalid server response\"}"
            }
        }

        // Remove trailing garbage
        val lastBrace = cleaned.lastIndexOf('}')
        val lastBracket = cleaned.lastIndexOf(']')
        val jsonEnd = maxOf(lastBrace, lastBracket)

        if (jsonEnd > 0 && jsonEnd < cleaned.length - 1) {
            val afterJson = cleaned.substring(jsonEnd + 1)
            if (afterJson.isNotBlank()) {
                Log.w(TAG, "cleanJsonResponse: Content after JSON: $afterJson")
                cleaned = cleaned.substring(0, jsonEnd + 1)
            }
        }

        return cleaned
    }

    private fun showLoadingState() {
        Log.d(TAG, "showLoadingState: Setting loading state")
        try {
            usernameTextView.text = "Loading..."
            displayNameTextView.text = "Loading..."
            bioTextView.text = ""
            postsCountTextView.text = "0"
            followersCountTextView.text = "0"
            followingCountTextView.text = "0"

            followButton.isEnabled = false
            followButton.alpha = 0.5f
            messageButton.isEnabled = false
            messageButton.alpha = 0.5f
        } catch (e: Exception) {
            Log.e(TAG, "showLoadingState: Error", e)
        }
    }

    private fun initializeViews() {
        Log.d(TAG, "initializeViews: Initializing views")
        try {
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
            Log.d(TAG, "initializeViews: All views initialized")
        } catch (e: Exception) {
            Log.e(TAG, "initializeViews: Error", e)
            throw e
        }
    }

    private fun loadUserProfileFromDb(): Boolean {
        Log.d(TAG, "loadUserProfileFromDb: Loading profile for targetUid=$targetUid")
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
                    Log.d(TAG, "loadUserProfileFromDb: Found user: ${user.username}")
                    populateProfileData(user)
                    hasData = true
                } catch (e: Exception) {
                    Log.e(TAG, "loadUserProfileFromDb: Error reading user", e)
                }
            } else {
                Log.d(TAG, "loadUserProfileFromDb: User not found in DB")
            }
            cursor.close()

            if (hasData) {
                loadUserPostsFromDb()
            }

            return hasData
        } catch (e: Exception) {
            Log.e(TAG, "loadUserProfileFromDb: Exception", e)
            return false
        }
    }

    private fun populateProfileData(user: User) {
        Log.d(TAG, "populateProfileData: Populating UI for ${user.username}")
        try {
            runOnUiThread {
                targetUsername = user.username
                usernameTextView.text = user.username
                displayNameTextView.text = user.fullName.takeIf { it.isNotBlank() } ?: user.username
                bioTextView.text = user.bio.takeIf { it.isNotBlank() } ?: "No bio available."

                try {
                    profileImageView.loadUserAvatar(user.uid, meUid, R.drawable.default_avatar)
                } catch (e: Exception) {
                    Log.e(TAG, "populateProfileData: Error loading avatar", e)
                    profileImageView.setImageResource(R.drawable.default_avatar)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "populateProfileData: Error", e)
        }
    }

    private fun fetchUserProfileFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d(TAG, "fetchUserProfileFromApi: Offline, skipping")
            if (!hasLoadedProfile) {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val url = "${AppGlobals.BASE_URL}user_profile_get.php?uid=$meUid&targetUid=$targetUid"
        Log.d(TAG, "fetchUserProfileFromApi: Fetching from: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchUserProfileFromApi: Response received, length=${response.length}")
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)

                    Log.d(TAG, "fetchUserProfileFromApi: success=${json.getBoolean("success")}")

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val userObj = dataObj.getJSONObject("user")
                        val relationshipObj = dataObj.getJSONObject("relationship")

                        val uid = userObj.getString("uid")
                        val username = userObj.getString("username")
                        val fullName = userObj.getString("fullName")
                        val bio = userObj.optString("bio", "")
                        val profilePictureUrl = userObj.optString("profilePictureUrl", "")
                        val photo = userObj.optString("photo", "")
                        val postsCount = userObj.getInt("postsCount")
                        val followersCount = userObj.getInt("followersCount")
                        val followingCount = userObj.getInt("followingCount")

                        Log.d(TAG, "fetchUserProfileFromApi: username=$username, fullName=$fullName")

                        // Save to DB
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
                        Log.d(TAG, "fetchUserProfileFromApi: Saved to DB")

                        // Update UI
                        runOnUiThread {
                            try {
                                targetUsername = username
                                usernameTextView.text = username
                                displayNameTextView.text = fullName
                                bioTextView.text = bio.takeIf { it.isNotBlank() } ?: "No bio available."

                                profileImageView.loadUserAvatar(uid, meUid, R.drawable.default_avatar)

                                postsCountTextView.text = postsCount.toString()
                                followersCountTextView.text = followersCount.toString()
                                followingCountTextView.text = followingCount.toString()

                                isFollowing = relationshipObj.getBoolean("isFollowing")
                                isRequested = relationshipObj.getBoolean("isRequested")

                                Log.d(TAG, "fetchUserProfileFromApi: isFollowing=$isFollowing, isRequested=$isRequested")

                                setupFollowButton(isFollowing, isRequested)

                                messageButton.isEnabled = true
                                messageButton.alpha = 1.0f

                                hasLoadedProfile = true
                                Log.d(TAG, "fetchUserProfileFromApi: UI updated successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "fetchUserProfileFromApi: Error updating UI", e)
                            }
                        }
                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.e(TAG, "fetchUserProfileFromApi: API error: $errorMsg")
                        runOnUiThread {
                            Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchUserProfileFromApi: Parse error", e)
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Error loading profile", Toast.LENGTH_LONG).show()
                    }
                }
            },
            { error ->
                Log.e(TAG, "fetchUserProfileFromApi: Network error", error)
                runOnUiThread {
                    if (!hasLoadedProfile) {
                        Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        queue.add(stringRequest)
    }

    private fun loadUserPostsFromDb() {
        Log.d(TAG, "loadUserPostsFromDb: Loading posts for targetUid=$targetUid")
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

            Log.d(TAG, "loadUserPostsFromDb: Loaded ${postList.size} posts")
        } catch (e: Exception) {
            Log.e(TAG, "loadUserPostsFromDb: Exception", e)
        }
    }

    private fun fetchUserPostsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d(TAG, "fetchUserPostsFromApi: Offline, skipping")
            return
        }

        val url = "${AppGlobals.BASE_URL}profile_posts_get.php?targetUid=$targetUid"
        Log.d(TAG, "fetchUserPostsFromApi: Fetching from: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchUserPostsFromApi: Response received")
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)

                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        Log.d(TAG, "fetchUserPostsFromApi: Processing ${dataArray.length()} posts")

                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid))

                            for (i in 0 until dataArray.length()) {
                                val postObj = dataArray.getJSONObject(i)
                                val cv = ContentValues().apply {
                                    put(DB.Post.COLUMN_POST_ID, postObj.getString("postId"))
                                    put(DB.Post.COLUMN_UID, postObj.getString("uid"))
                                    put(DB.Post.COLUMN_USERNAME, postObj.optString("username", ""))
                                    put(DB.Post.COLUMN_CAPTION, postObj.optString("caption", ""))
                                    put(DB.Post.COLUMN_IMAGE_URL, postObj.optString("imageUrl", ""))
                                    put(DB.Post.COLUMN_IMAGE_BASE64, postObj.optString("imageBase64", ""))
                                    put(DB.Post.COLUMN_CREATED_AT, postObj.getLong("createdAt"))
                                    put(DB.Post.COLUMN_LIKE_COUNT, postObj.optLong("likeCount", 0))
                                    put(DB.Post.COLUMN_COMMENT_COUNT, postObj.optLong("commentCount", 0))
                                }
                                db.insert(DB.Post.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }

                        loadUserPostsFromDb()
                        Log.d(TAG, "fetchUserPostsFromApi: Posts saved and reloaded")
                    } else {
                        Log.w(TAG, "fetchUserPostsFromApi: API returned success=false")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchUserPostsFromApi: Error", e)
                }
            },
            { error ->
                Log.e(TAG, "fetchUserPostsFromApi: Network error", error)
            }
        )
        queue.add(stringRequest)
    }

    private fun setupPostsGrid() {
        Log.d(TAG, "setupPostsGrid: Setting up")
        try {
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
            Log.d(TAG, "setupPostsGrid: Complete")
        } catch (e: Exception) {
            Log.e(TAG, "setupPostsGrid: Error", e)
        }
    }

    private fun openPostDetail(post: Post) {
        Log.d(TAG, "openPostDetail: Opening postId=${post.postId}")
        try {
            val intent = Intent(this, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", post.postId)
                putExtra("USER_ID", post.uid)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPostDetail: Error", e)
            Toast.makeText(this, "Error opening post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMessageButton() {
        Log.d(TAG, "setupMessageButton: Setting up")
        try {
            messageButton.setOnClickListener {
                Log.d(TAG, "Message button clicked")
                createChatAndOpen()
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupMessageButton: Error", e)
        }
    }

    private fun createChatAndOpen() {
        if (!isNetworkAvailable(this)) {
            Log.d(TAG, "createChatAndOpen: Offline, opening chat directly")
            openChatActivity()
            return
        }

        Log.d(TAG, "createChatAndOpen: Creating chat with targetUid=$targetUid")
        val url = "${AppGlobals.BASE_URL}chat_create.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                Log.d(TAG, "createChatAndOpen: Response received")
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val chatId = dataObj.getString("chatId")
                        Log.d(TAG, "createChatAndOpen: Chat ready, ID=$chatId")
                        openChatActivity()
                    } else {
                        val errorMsg = json.optString("message", "Failed to create chat")
                        Log.e(TAG, "createChatAndOpen: Error: $errorMsg")
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "createChatAndOpen: Parse error", e)
                    openChatActivity()
                }
            },
            { error ->
                Log.e(TAG, "createChatAndOpen: Network error", error)
                openChatActivity()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "uid1" to meUid,
                    "uid2" to targetUid
                )
            }
        }
        queue.add(stringRequest)
    }

    private fun openChatActivity() {
        Log.d(TAG, "openChatActivity: Opening with targetUid=$targetUid, username=$targetUsername")
        try {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", targetUid)
                putExtra("username", targetUsername)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openChatActivity: Error", e)
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFollowButton(following: Boolean, requested: Boolean) {
        Log.d(TAG, "setupFollowButton: following=$following, requested=$requested")
        try {
            followButton.visibility = View.VISIBLE
            followButton.isEnabled = true
            followButton.alpha = 1.0f
            setFollowState(following, requested)

            followButton.setOnClickListener {
                Log.d(TAG, "Follow button clicked: isFollowing=$isFollowing, isRequested=$isRequested")
                try {
                    if (isFollowing) {
                        Log.d(TAG, "Unfollowing user")
                        unfollowUser()
                    } else if (isRequested) {
                        Log.d(TAG, "Canceling follow request")
                        unfollowUser()
                    } else {
                        Log.d(TAG, "Following user")
                        followUser()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Follow button click error", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            Log.d(TAG, "setupFollowButton: Complete")
        } catch (e: Exception) {
            Log.e(TAG, "setupFollowButton: Error", e)
        }
    }

    private fun setFollowState(following: Boolean, requested: Boolean) {
        Log.d(TAG, "setFollowState: following=$following, requested=$requested")
        try {
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
                    Log.d(TAG, "setFollowState: Set to Following")
                }
                requested -> {
                    followButton.text = "Requested"
                    followButton.setBackgroundResource(R.drawable.requested_button)
                    ViewCompat.setBackgroundTintList(followButton, null)
                    followButton.setTextColor(getColor(R.color.gray))
                    Log.d(TAG, "setFollowState: Set to Requested")
                }
                else -> {
                    followButton.text = "Follow"
                    followButton.setBackgroundResource(R.drawable.message_bttn)
                    ViewCompat.setBackgroundTintList(followButton, ColorStateList.valueOf(getColor(R.color.smd_theme)))
                    followButton.setTextColor(getColor(android.R.color.white))
                    Log.d(TAG, "setFollowState: Set to Follow")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "setFollowState: Error", e)
        }
    }

    private fun followUser() {
        Log.d(TAG, "followUser: Starting - meUid=$meUid, targetUid=$targetUid")

        if (meUid.isEmpty() || targetUid.isEmpty()) {
            Log.e(TAG, "followUser: Missing UIDs")
            Toast.makeText(this, "Error: User ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "followUser: No network available")
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "${AppGlobals.BASE_URL}follow_user.php"
        Log.d(TAG, "followUser: Sending request to: $url")

        // Disable button to prevent double-clicks
        followButton.isEnabled = false
        followButton.alpha = 0.5f

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                Log.d(TAG, "followUser: Response received, length=${response.length}")
                Log.d(TAG, "followUser: Response: ${response.take(500)}")

                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    val success = json.getBoolean("success")

                    Log.d(TAG, "followUser: success=$success")

                    if (success) {
                        val dataObj = json.getJSONObject("data")
                        val status = dataObj.getString("status")

                        Log.d(TAG, "followUser: status=$status")

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
                                else -> {
                                    Log.w(TAG, "followUser: Unknown status: $status")
                                }
                            }

                            // Re-enable button
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }

                        fetchUserProfileFromApi()
                    } else {
                        val message = json.optString("message", "Failed to follow")
                        Log.e(TAG, "followUser: API error: $message")
                        runOnUiThread {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "followUser: Parse error", e)
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show()
                        followButton.isEnabled = true
                        followButton.alpha = 1.0f
                    }
                }
            },
            { error ->
                Log.e(TAG, "followUser: Network error", error)
                error.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show()
                    followButton.isEnabled = true
                    followButton.alpha = 1.0f
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = hashMapOf(
                    "uid" to meUid,
                    "targetUid" to targetUid
                )
                Log.d(TAG, "followUser: Params: $params")
                return params
            }
        }
        queue.add(stringRequest)
        Log.d(TAG, "followUser: Request added to queue")
    }

    private fun unfollowUser() {
        Log.d(TAG, "unfollowUser: Starting - meUid=$meUid, targetUid=$targetUid")

        if (meUid.isEmpty() || targetUid.isEmpty()) {
            Log.e(TAG, "unfollowUser: Missing UIDs")
            Toast.makeText(this, "Error: User ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "unfollowUser: No network available")
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "${AppGlobals.BASE_URL}unfollow_user.php"
        Log.d(TAG, "unfollowUser: Sending request to: $url")

        // Disable button
        followButton.isEnabled = false
        followButton.alpha = 0.5f

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                Log.d(TAG, "unfollowUser: Response received")
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)

                    if (json.getBoolean("success")) {
                        Log.d(TAG, "unfollowUser: Success")
                        runOnUiThread {
                            setFollowState(false, false)
                            Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show()
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }
                        fetchUserProfileFromApi()
                    } else {
                        val message = json.optString("message", "Failed to unfollow")
                        Log.e(TAG, "unfollowUser: Error: $message")
                        runOnUiThread {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            followButton.isEnabled = true
                            followButton.alpha = 1.0f
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "unfollowUser: Parse error", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error processing response", Toast.LENGTH_SHORT).show()
                        followButton.isEnabled = true
                        followButton.alpha = 1.0f
                    }
                }
            },
            { error ->
                Log.e(TAG, "unfollowUser: Network error", error)
                runOnUiThread {
                    Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show()
                    followButton.isEnabled = true
                    followButton.alpha = 1.0f
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = hashMapOf(
                    "uid" to meUid,
                    "targetUid" to targetUid
                )
                Log.d(TAG, "unfollowUser: Params: $params")
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun setupBottomNavigationBar() {
        Log.d(TAG, "setupBottomNavigationBar: Setting up")
        // Setup nav if needed
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}