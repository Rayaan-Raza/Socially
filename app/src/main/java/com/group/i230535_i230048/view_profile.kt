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
        navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_profile)

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        targetUid = intent.getStringExtra("userId")
            ?: intent.getStringExtra("USER_ID")
                    ?: intent.getStringExtra("uid")
                    ?: ""

        Log.d("view_profile", "Target UID: $targetUid, My UID: $meUid")

        if (targetUid.isEmpty() || meUid.isEmpty()) {
            Toast.makeText(this, "User ID is missing.", Toast.LENGTH_LONG).show()
            finish(); return
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

        showLoadingState()
    }

    override fun onStart() {
        super.onStart()

        val hasLocalData = loadUserProfileFromDb()

        fetchUserProfileFromApi()
        fetchUserPostsFromApi()

        if (!hasLocalData) {
            Log.d("view_profile", "No local data found, waiting for API response")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Helper function to clean PHP error output from JSON response.
     * PHP sometimes outputs HTML warnings/errors before the JSON, which breaks parsing.
     * This strips out HTML junk and returns only the JSON part.
     */
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        Log.d("view_profile", "Raw API response (first 500 chars): ${cleaned.take(500)}")
        Log.d("view_profile", "Response length: ${cleaned.length}, First char: '${cleaned.firstOrNull()}'")

        // Check if this is a pure PHP error (Fatal error means no JSON at all)
        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Uncaught")) {
            Log.e("view_profile", "❌ PHP Fatal error detected - no valid JSON in response!")

            // Extract the actual error message for debugging
            val errorMessage = if (cleaned.contains("Unknown column")) {
                "Database column mismatch. Tell partner to check: from_uid should be sender_uid, to_uid should be receiver_uid in follow_requests table query"
            } else {
                "PHP server error"
            }

            Log.e("view_profile", "FIX NEEDED: $errorMessage")
            return "{\"success\": false, \"message\": \"$errorMessage\"}"
        }

        // Check if response starts with JSON
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            Log.w("view_profile", "⚠️ Response doesn't start with JSON! First 50 chars: ${cleaned.take(50)}")

            // Try to find actual JSON (look for {" pattern which is more reliable)
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
                Log.e("view_profile", "Content before JSON: $beforeJson")
                cleaned = cleaned.substring(jsonStart)
                Log.d("view_profile", "Cleaned JSON starts with: ${cleaned.take(100)}")
            } else if (jsonStart == -1) {
                Log.e("view_profile", "❌ No JSON found in response! Full response: $cleaned")
                return "{\"success\": false, \"message\": \"Invalid server response\"}"
            }
        }

        // Also check for and remove any trailing garbage after JSON
        val lastBrace = cleaned.lastIndexOf('}')
        val lastBracket = cleaned.lastIndexOf(']')
        val jsonEnd = maxOf(lastBrace, lastBracket)

        if (jsonEnd > 0 && jsonEnd < cleaned.length - 1) {
            val afterJson = cleaned.substring(jsonEnd + 1)
            if (afterJson.isNotBlank()) {
                Log.w("view_profile", "Content after JSON: $afterJson")
                cleaned = cleaned.substring(0, jsonEnd + 1)
            }
        }

        return cleaned
    }

    private fun showLoadingState() {
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
        Log.d("view_profile", "Loading profile from DB for UID: $targetUid")
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
                Log.d("view_profile", "Found user in DB: ${user.username}")
                populateProfileData(user)
                hasData = true
            } catch (e: Exception) {
                Log.e("view_profile", "Error reading user from DB: ${e.message}")
            }
        } else {
            Log.d("view_profile", "User not found in local DB")
        }
        cursor.close()

        if (hasData) {
            loadUserPostsFromDb()
        }

        return hasData
    }

    private fun fetchUserProfileFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("view_profile", "Offline, skipping profile fetch")
            if (!hasLoadedProfile) {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val url = AppGlobals.BASE_URL + "user_profile_get.php?uid=$meUid&targetUid=$targetUid"
        Log.d("view_profile", "Fetching profile from: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d("view_profile", "Got API response, length: ${response.length}")
                try {
                    val cleanedResponse = cleanJsonResponse(response)

                    val json = JSONObject(cleanedResponse)
                    Log.d("view_profile", "Parsed JSON success: ${json.getBoolean("success")}")

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        Log.d("view_profile", "Data object keys: ${dataObj.keys().asSequence().toList()}")

                        val userObj = dataObj.getJSONObject("user")
                        val relationshipObj = dataObj.getJSONObject("relationship")

                        val uid = userObj.getString("uid")
                        val username = userObj.getString("username")
                        val fullName = userObj.getString("fullName")
                        val bio = userObj.optString("bio", "")
                        val profilePictureUrl = userObj.optString("profilePictureUrl", "")
                        val postsCount = userObj.getInt("postsCount")
                        val followersCount = userObj.getInt("followersCount")
                        val followingCount = userObj.getInt("followingCount")

                        Log.d("view_profile", "✅ Parsed user - username: $username, fullName: $fullName, uid: $uid")

                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, uid)
                        cv.put(DB.User.COLUMN_USERNAME, username)
                        cv.put(DB.User.COLUMN_FULL_NAME, fullName)
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, profilePictureUrl)
                        cv.put(DB.User.COLUMN_BIO, bio)

                        val result = dbHelper.writableDatabase.insertWithOnConflict(
                            DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        Log.d("view_profile", "Saved user to DB, result: $result")

                        runOnUiThread {
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
                            setupFollowButton(isFollowing, isRequested)

                            messageButton.isEnabled = true
                            messageButton.alpha = 1.0f

                            hasLoadedProfile = true
                            Log.d("view_profile", "✅ UI updated successfully")
                        }
                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.e("view_profile", "API returned success=false: $errorMsg")
                        runOnUiThread {
                            Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("view_profile", "❌ Error parsing profile: ${e.message}")
                    Log.e("view_profile", "Stack trace: ", e)
                    e.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            { error ->
                Log.e("view_profile", "Volley error fetching profile: ${error.message}")
                Log.e("view_profile", "Network response: ${error.networkResponse}")
                if (error.networkResponse != null) {
                    Log.e("view_profile", "Status code: ${error.networkResponse.statusCode}")
                    try {
                        Log.e("view_profile", "Response data: ${String(error.networkResponse.data)}")
                    } catch (e: Exception) {
                        Log.e("view_profile", "Could not read response data")
                    }
                }
                error.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        queue.add(stringRequest)
    }

    private fun populateProfileData(user: User) {
        Log.d("view_profile", "Populating UI with user: ${user.username}")
        targetUsername = user.username
        usernameTextView.text = user.username.ifBlank { "user" }
        displayNameTextView.text = user.fullName.ifBlank { "User" }
        bioTextView.text = user.bio.takeIf { it.isNotBlank() } ?: "No bio available."
        profileImageView.loadUserAvatar(user.uid, meUid, R.drawable.default_avatar)

        if (!targetUsername.isNullOrEmpty()) {
            messageButton.isEnabled = true
            messageButton.alpha = 1.0f
        }

        hasLoadedProfile = true
    }

    private fun loadUserPostsFromDb() {
        postList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.Post.TABLE_NAME, null,
            "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid),
            null, null, DB.Post.COLUMN_CREATED_AT + " DESC"
        )

        while (cursor.moveToNext()) {
            try {
                postList.add(
                    Post(
                        postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                        uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                        imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)) ?: "",
                        imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)) ?: "",
                        caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)) ?: "",
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                        likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                        commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                    )
                )
            } catch (e: Exception) {
                Log.e("view_profile", "Error parsing post from DB: ${e.message}")
            }
        }
        cursor.close()
        postsAdapter.notifyDataSetChanged()
        postsCountTextView.text = postList.size.toString()
    }

    private fun fetchUserPostsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("view_profile", "Offline, skipping posts fetch")
            return
        }

        val url = AppGlobals.BASE_URL + "profile_posts_get.php?targetUid=$targetUid"
        Log.d("view_profile", "Fetching posts from: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d("view_profile", "Posts API Response length: ${response.length}")
                try {
                    val cleanedResponse = cleanJsonResponse(response)

                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        Log.d("view_profile", "Found ${dataArray.length()} posts")

                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(targetUid))

                            for (i in 0 until dataArray.length()) {
                                val postObj = dataArray.getJSONObject(i)
                                val cv = ContentValues()
                                cv.put(DB.Post.COLUMN_POST_ID, postObj.getString("postId"))
                                cv.put(DB.Post.COLUMN_UID, postObj.getString("uid"))
                                cv.put(DB.Post.COLUMN_USERNAME, postObj.optString("username", ""))
                                cv.put(DB.Post.COLUMN_CAPTION, postObj.optString("caption", ""))
                                cv.put(DB.Post.COLUMN_IMAGE_URL, postObj.optString("imageUrl", ""))
                                cv.put(DB.Post.COLUMN_IMAGE_BASE64, postObj.optString("imageBase64", ""))
                                cv.put(DB.Post.COLUMN_CREATED_AT, postObj.getLong("createdAt"))
                                cv.put(DB.Post.COLUMN_LIKE_COUNT, postObj.optLong("likeCount", 0))
                                cv.put(DB.Post.COLUMN_COMMENT_COUNT, postObj.optLong("commentCount", 0))
                                db.insert(DB.Post.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        loadUserPostsFromDb()
                    }
                } catch (e: Exception) {
                    Log.e("view_profile", "Error parsing posts: ${e.message}")
                    e.printStackTrace()
                }
            },
            { error ->
                Log.w("view_profile", "Volley error fetching posts: ${error.message}")
            }
        )
        queue.add(stringRequest)
    }

    private fun openPostDetail(post: Post) {
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
        }
        startActivity(intent)
    }

    private fun setupPostsGrid() {
        postsRecyclerView.layoutManager = GridLayoutManager(this, 3)
        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            openPostDetail(clickedPost)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    private fun setupMessageButton() {
        messageButton.visibility = View.VISIBLE
        messageButton.isEnabled = false
        messageButton.alpha = 0.5f

        messageButton.setOnClickListener {
            if (targetUsername.isNullOrEmpty()) {
                Toast.makeText(this, "Loading user data...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create chat first, then open ChatActivity
            createChatAndOpen()
        }
    }

    /**
     * Creates a chat session on the server before opening ChatActivity.
     * Uses chat_create.php to ensure the chat exists.
     */
    private fun createChatAndOpen() {
        if (!isNetworkAvailable(this)) {
            // If offline, just open chat directly (it will work offline)
            openChatActivity()
            return
        }

        Log.d("view_profile", "Creating chat with user: $targetUid")
        val url = AppGlobals.BASE_URL + "chat_create.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val chatId = dataObj.getString("chatId")
                        val createdNow = dataObj.optBoolean("createdNow", false)

                        Log.d("view_profile", "✅ Chat ready. ID: $chatId, createdNow: $createdNow")
                        openChatActivity()
                    } else {
                        val errorMsg = json.optString("message", "Failed to create chat")
                        Log.e("view_profile", "Chat creation failed: $errorMsg")
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("view_profile", "Error creating chat: ${e.message}")
                    // Still open chat activity - it might work anyway
                    openChatActivity()
                }
            },
            { error ->
                Log.e("view_profile", "Network error creating chat: ${error.message}")
                // Still open chat activity - offline mode will handle it
                openChatActivity()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid1"] = meUid
                params["uid2"] = targetUid
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun openChatActivity() {
        Log.d("view_profile", "Opening chat with uid: $targetUid, username: $targetUsername")
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
            if (isFollowing) {
                unfollowUser()
            } else if (isRequested) {
                unfollowUser()
            } else {
                followUser()
            }
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
        val url = AppGlobals.BASE_URL + "follow_user.php"
        Log.d("view_profile", "Following user: $targetUid")

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val status = dataObj.getString("status")

                        when (status) {
                            "following" -> setFollowState(true, false)
                            "requested" -> setFollowState(false, true)
                        }

                        fetchUserProfileFromApi()
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing follow: ${e.message}") }
            },
            { error -> Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid"] = meUid
                params["targetUid"] = targetUid
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun unfollowUser() {
        val url = AppGlobals.BASE_URL + "unfollow_user.php"
        Log.d("view_profile", "Unfollowing user: $targetUid")

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleanedResponse = cleanJsonResponse(response)
                    val json = JSONObject(cleanedResponse)
                    if (json.getBoolean("success")) {
                        setFollowState(false, false)
                        fetchUserProfileFromApi()
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Log.e("view_profile", "Error parsing unfollow: ${e.message}") }
            },
            { error -> Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_SHORT).show() }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid"] = meUid
                params["targetUid"] = targetUid
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun setupBottomNavigationBar() {
        // Setup nav if needed
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}