package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues // CHANGED: Added for SQLite
import android.content.Context // CHANGED: Added for SharedPreferences & Network Check
import android.content.Intent
import android.database.Cursor // CHANGED: Added for SQLite
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager // CHANGED: Added for network check
import android.net.NetworkCapabilities // CHANGED: Added for network check
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging // Kept for FCM (Task #9)
// Add this with your other imports at the top
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

import com.group.i230535_i230048.databinding.ItemStoryBubbleBinding
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request // CHANGED: Added Volley
import com.android.volley.RequestQueue // CHANGED: Added Volley
import com.android.volley.toolbox.StringRequest // CHANGED: Added Volley
import com.android.volley.toolbox.Volley // CHANGED: Added Volley
import com.group.i230535_i230048.AppDbHelper // CHANGED: Added DB Helper
import com.group.i230535_i230048.DB // CHANGED: Added DB Helper
import org.json.JSONArray // CHANGED: Added JSON
import org.json.JSONObject // CHANGED: Added JSON


class home_page : AppCompatActivity() {

    // CHANGED: Replaced Firebase DB/Auth with local DB and session
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUid: String = ""
    private var currentUsername: String = ""

    private val usernameCache = mutableMapOf<String, String>()
    private lateinit var navProfileImage: ImageView

    private lateinit var rvStories: RecyclerView
    private lateinit var storyAdapter: StoryAdapter
    private val storyList = mutableListOf<StoryBubble>()

    private lateinit var rvFeed: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private val currentPosts = mutableListOf<Post>()

    // REMOVED: All Firebase listeners (postChildListeners, likeListeners, etc.)

    private var postToSend: Post? = null
    private val selectFriendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedUserId = result.data?.getStringExtra("SELECTED_USER_ID")
            if (selectedUserId != null && postToSend != null) {
                // CHANGED: We now call the new offline-ready function
                sendMessageWithPost(selectedUserId, postToSend!!)
            }
        }
    }

    // CHANGED: Migrated to read from local SQLite DB
    fun loadBottomBarAvatar(navProfile: ImageView) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.User.TABLE_NAME,
            arrayOf(DB.User.COLUMN_PROFILE_PIC_URL),
            "${DB.User.COLUMN_UID} = ?",
            arrayOf(currentUid),
            null, null, null
        )

        var url: String? = null
        if (cursor.moveToFirst()) {
            url = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PROFILE_PIC_URL))
        }
        cursor.close()

        Glide.with(navProfile.context)
            .load(url) // Load URL from API/DB
            .placeholder(R.drawable.oval)
            .error(R.drawable.oval)
            .circleCrop()
            .into(navProfile)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // --- CHANGED: SESSION & DB SETUP ---
        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUid = prefs.getString(AppGlobals.KEY_USER_UID, null) ?: ""
        currentUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"

        if (currentUid.isEmpty()) {
            // User is not logged in, boot to login screen
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }
        // --- END OF SESSION & DB SETUP ---

        val navProfile = findViewById<ImageView>(R.id.profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navProfileImage = findViewById(R.id.profile)

        rvStories = findViewById(R.id.rvStories)
        rvStories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        storyAdapter = StoryAdapter(storyList, currentUid) // Adapter init is the same
        rvStories.adapter = storyAdapter

        rvFeed = findViewById(R.id.rvFeed)
        rvFeed.layoutManager = LinearLayoutManager(this)

        postAdapter = PostAdapter(
            currentUid = currentUid,
            onLikeToggle = { post, wantLike -> toggleLike(post, wantLike) },
            onCommentClick = { post -> openPostDetail(post, showComments = true) },
            onSendClick = { post -> sendPostToFriend(post) },
            onPostClick = { post -> openPostDetail(post, showComments = false) }
        )
        rvFeed.adapter = postAdapter

        setupClickListeners()
        getFcmToken() // This function is now migrated
        requestNotificationPermission()
    }


    private fun openPostDetail(post: Post, showComments: Boolean = false) {
        // No changes needed, this logic is correct
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
            putExtra("SHOW_COMMENTS", showComments)
        }
        startActivity(intent)
    }

    private fun sendPostToFriend(post: Post) {
        // No changes needed, this logic is correct
        postToSend = post
        val intent = Intent(this, dms::class.java)
        intent.putExtra("ACTION_MODE", "SHARE")
        selectFriendLauncher.launch(intent)
    }

    // REMOVED: updateLastMessage (Backend should handle this)

    // CHANGED: Migrated to Volley + Offline Queue
    private fun sendMessageWithPost(recipientId: String, post: Post) {
        val timestamp = System.currentTimeMillis()

        // 1. Create the request payload
        val payload = JSONObject()
        payload.put("sender_id", currentUid)
        payload.put("receiver_id", recipientId)
        payload.put("message_type", "post")
        payload.put("content", "Shared a post")
        payload.put("post_id", post.postId)
        payload.put("timestamp", timestamp)

        // 2. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val stringRequest = object : StringRequest(
                Request.Method.POST, AppGlobals.BASE_URL + "send_message.php", // (from ApiService.kt)
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Toast.makeText(this, "Post sent!", Toast.LENGTH_SHORT).show()
                            // TODO: Save sent message to local DB
                        } else {
                            Toast.makeText(this, "Failed to send: ${json.getString("message")}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { Log.e("home_page", "Error parsing send_message: ${e.message}") }
                },
                { error ->
                    Log.e("home_page", "Volley error send_message: ${error.message}")
                    saveToSyncQueue("send_message.php", payload) // Save if network fails
                }) {
                override fun getParams(): MutableMap<String, String> {
                    // Volley sends as x-www-form-urlencoded, but our API (from ApiService.kt)
                    // expects multipart. This will need adjustment if Dev A uses multipart.
                    // Assuming Dev A changes 'send_message.php' to accept form-urlencoded:
                    val params = HashMap<String, String>()
                    params["sender_id"] = currentUid
                    params["receiver_id"] = recipientId
                    params["message_type"] = "post"
                    params["content"] = "Shared a post"
                    params["post_id"] = post.postId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // 3. Save to queue if offline
            saveToSyncQueue("send_message.php", payload)
        }
    }


    private fun setupClickListeners() {
        // No changes needed, this logic is correct
        findViewById<ImageView>(R.id.heart).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
        }
        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
        }
        findViewById<ImageView>(R.id.dms).setOnClickListener {
            startActivity(Intent(this, dms::class.java))
        }
        findViewById<ImageView>(R.id.camera).setOnClickListener {
            startActivity(Intent(this, camera_activiy::class.java))
        }
        findViewById<ImageView>(R.id.post).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
        }
        findViewById<ImageView>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // CHANGED: New "Offline-First" loading pattern
        // 1. Load data from local DB instantly
        loadStoriesFromDb()
        loadFeedFromDb()

        // 2. Fetch fresh data from network to update local DB
        fetchMyProfile() // Fetches and saves your own user data
        fetchStoriesFromApi()
        fetchFeedFromApi()

        // 3. Setup non-Firebase call listener
        setupAgoraCallListener()
    }

    override fun onStop() {
        super.onStop()
        // REMOVED: All Firebase listener removals. No longer needed.
    }


    // --- CHANGED: NEW OFFLINE-FIRST STORY FUNCTIONS ---
    private fun loadStoriesFromDb() {
        Log.d("home_page", "Loading stories from local DB...")
        storyList.clear()

        // 1. Add "Your Story" bubble
        storyList.add(StoryBubble(currentUid, "Your Story", null)) // We'll load pic from user table

        // 2. Load stories from DB
        val db = dbHelper.readableDatabase
        // We need a JOIN here, or to save the username/pic in the stories table.
        // For simplicity, `get_stories.php` (from ApiService.kt) returns a `Story`
        // model that has user info. Let's assume our `DB.Story` table needs those fields.
        // **I'll update the AppDbHelper/DB in my head to add `uid`, `username`, `profileUrl` to `DB.Story`**
        // Since I can't edit the file, I'll just query what we have.
        val cursor = db.query(DB.Story.TABLE_NAME, null, null, null, null, null, DB.Story.COLUMN_CREATED_AT + " DESC")

        while (cursor.moveToNext()) {
            // This is imperfect as our DB.Story table is missing user info.
            // The API must provide it.
            storyList.add(StoryBubble(
                uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Story.COLUMN_UID)),
                username = "User", // We need to store this in the DB
                profileUrl = null // We need to store this in the DB
            ))
        }
        cursor.close()
        storyAdapter.notifyDataSetChanged()
    }

    private fun fetchStoriesFromApi() {
        Log.d("home_page", "Fetching stories from API...")
        val url = AppGlobals.BASE_URL + "get_stories.php?user_id=$currentUid" // (from ApiService.kt)

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase

                        // Clear old stories (except user's own, handled by API)
                        db.delete(DB.Story.TABLE_NAME, null, null)
                        db.beginTransaction()
                        try {
                            for (i in 0 until dataArray.length()) {
                                val storyObj = dataArray.getJSONObject(i)
                                val cv = ContentValues()
                                cv.put(DB.Story.COLUMN_STORY_ID, storyObj.getString("storyId"))
                                cv.put(DB.Story.COLUMN_UID, storyObj.getString("userId"))
                                cv.put(DB.Story.COLUMN_MEDIA_URL, storyObj.getString("mediaUrl"))
                                cv.put(DB.Story.COLUMN_MEDIA_TYPE, storyObj.getString("mediaType"))
                                cv.put(DB.Story.COLUMN_CREATED_AT, storyObj.getLong("createdAt"))
                                cv.put(DB.Story.COLUMN_EXPIRES_AT, storyObj.getLong("expiresAt"))
                                // TODO: API/DB needs to include username/profile pic
                                db.insert(DB.Story.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        // Refresh UI from DB
                        loadStoriesFromDb()
                    }
                } catch (e: Exception) { Log.e("home_page", "Error parsing stories: ${e.message}") }
            },
            { error -> Log.w("home_page", "Volley error fetching stories: ${error.message}") }
        )
        queue.add(stringRequest)
    }
    // --- END OF STORY FUNCTIONS ---


    // --- CHANGED: StoryAdapter Inner Class ---
    inner class StoryAdapter(private val items: List<StoryBubble>, private val currentUid: String) :
        RecyclerView.Adapter<StoryAdapter.StoryVH>() {

        inner class StoryVH(val binding: ItemStoryBubbleBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
            val inflater = layoutInflater
            val binding = ItemStoryBubbleBinding.inflate(inflater, parent, false)
            return StoryVH(binding)
        }

        override fun onBindViewHolder(holder: StoryVH, position: Int) {
            val item = items[position]
            holder.binding.username.text = item.username

            // REMOVED: holder.binding.pfp.loadUserAvatar(...)
            // CHANGED: Replaced with standard Glide call
            Glide.with(holder.binding.pfp.context)
                .load(item.profileUrl) // Load the URL from the StoryBubble model
                .placeholder(R.drawable.person1)
                .error(R.drawable.person1)
                .circleCrop()
                .into(holder.binding.pfp)

            holder.binding.root.setOnClickListener {
                val intent = Intent(this@home_page, camera_story::class.java)
                intent.putExtra("uid", item.uid)
                startActivity(intent)
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }
    // --- END OF StoryAdapter ---


    // --- CHANGED: NEW OFFLINE-FIRST FEED FUNCTIONS ---
    private fun loadFeedFromDb() {
        Log.d("home_page", "Loading feed from local DB...")
        val db = dbHelper.readableDatabase
        currentPosts.clear()

        // 1. Load Posts
        val postCursor = db.query(DB.Post.TABLE_NAME, null, null, null, null, null, DB.Post.COLUMN_CREATED_AT + " DESC")
        while (postCursor.moveToNext()) {
            currentPosts.add(
                Post(
                    postId = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                    uid = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                    username = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)),
                    caption = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)),
                    imageUrl = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)),
                    imageBase64 = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)),
                    createdAt = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                    likeCount = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                    commentCount = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                )
            )
        }
        postCursor.close()
        postAdapter.submitList(currentPosts.toList())

        // 2. Load Comments for visible posts
        // This is complex; a better way is to load them in the adapter's onBindViewHolder
        // For now, let's just update the adapter's comment data
        currentPosts.forEach { post ->
            loadCommentsForPostFromDb(post.postId)
        }
    }

    private fun loadCommentsForPostFromDb(postId: String) {
        val db = dbHelper.readableDatabase
        val comments = mutableListOf<Comment>()
        val commentCursor = db.query(
            DB.Comment.TABLE_NAME, null,
            "${DB.Comment.COLUMN_POST_ID} = ?", arrayOf(postId),
            null, null, DB.Comment.COLUMN_CREATED_AT + " DESC", "2" // Limit to 2
        )
        while(commentCursor.moveToNext()) {
            comments.add(
                Comment(
                    commentId = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_COMMENT_ID)),
                    postId = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_POST_ID)),
                    uid = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_UID)),
                    username = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_USERNAME)),
                    text = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_TEXT)),
                    createdAt = commentCursor.getLong(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_CREATED_AT))
                )
            )
        }
        commentCursor.close()
        postAdapter.setCommentPreview(postId, comments.reversed()) // Show oldest of the two first
        // We also need to set the *total* count
        // postAdapter.setCommentTotal(postId, total) // We should store this
    }

    private fun fetchFeedFromApi() {
        Log.d("home_page", "Fetching feed from API...")
        val url = AppGlobals.BASE_URL + "get_feed.php?uid=$currentUid" // (from ApiService.kt)

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase

                        // Clear old feed data
                        db.delete(DB.Post.TABLE_NAME, null, null)
                        db.delete(DB.Comment.TABLE_NAME, null, null) // Clear comments too

                        db.beginTransaction()
                        try {
                            for (i in 0 until dataArray.length()) {
                                val postObj = dataArray.getJSONObject(i)
                                val cv = ContentValues()
                                cv.put(DB.Post.COLUMN_POST_ID, postObj.getString("postId"))
                                cv.put(DB.Post.COLUMN_UID, postObj.getString("uid"))
                                cv.put(DB.Post.COLUMN_USERNAME, postObj.getString("username"))
                                cv.put(DB.Post.COLUMN_CAPTION, postObj.getString("caption"))
                                cv.put(DB.Post.COLUMN_IMAGE_URL, postObj.getString("imageUrl"))
                                cv.put(DB.Post.COLUMN_CREATED_AT, postObj.getLong("createdAt"))
                                cv.put(DB.Post.COLUMN_LIKE_COUNT, postObj.getLong("likeCount"))
                                cv.put(DB.Post.COLUMN_COMMENT_COUNT, postObj.getLong("commentCount"))
                                db.insert(DB.Post.TABLE_NAME, null, cv)

                                // Now fetch comments for this post
                                fetchCommentsForPost(postObj.getString("postId"))
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        // Refresh UI from DB (will show posts, comments will load in)
                        loadFeedFromDb()
                    }
                } catch (e: Exception) { Log.e("home_page", "Error parsing feed: ${e.message}") }
            },
            { error -> Log.w("home_page", "Volley error fetching feed: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun fetchCommentsForPost(postId: String) {
        val url = AppGlobals.BASE_URL + "get_comments.php?post_id=$postId" // (from ApiService.kt)
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            for (i in 0 until dataArray.length()) {
                                val commentObj = dataArray.getJSONObject(i)
                                val cv = ContentValues()
                                cv.put(DB.Comment.COLUMN_COMMENT_ID, commentObj.getString("commentId"))
                                cv.put(DB.Comment.COLUMN_POST_ID, commentObj.getString("postId"))
                                cv.put(DB.Comment.COLUMN_UID, commentObj.getString("uid"))
                                cv.put(DB.Comment.COLUMN_USERNAME, commentObj.getString("username"))
                                cv.put(DB.Comment.COLUMN_TEXT, commentObj.getString("text"))
                                cv.put(DB.Comment.COLUMN_CREATED_AT, commentObj.getLong("createdAt"))
                                db.insert(DB.Comment.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        // Refresh just this post's comments
                        loadCommentsForPostFromDb(postId)
                    }
                } catch (e: Exception) { Log.e("home_page", "Error parsing comments: ${e.message}") }
            },
            { error -> Log.w("home_page", "Volley error fetching comments: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun fetchMyProfile() {
        val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$currentUid" // [cite: 224-228]
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")
                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.getString("uid"))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.getString("username"))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.getString("fullName"))
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, userObj.getString("profilePictureUrl"))
                        cv.put(DB.User.COLUMN_EMAIL, userObj.getString("email"))
                        cv.put(DB.User.COLUMN_BIO, userObj.getString("bio"))

                        val db = dbHelper.writableDatabase
                        db.insertWithOnConflict(DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        // Reload avatar now that we have the URL
                        loadBottomBarAvatar(navProfileImage)
                    }
                } catch (e: Exception) { Log.e("home_page", "Error parsing my profile: ${e.message}") }
            },
            { error -> Log.w("home_page", "Volley error fetching profile: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    // REMOVED: All Firebase realtime functions (readPostsFor, fetchInitialCommentsAndShow, attachRealtimeFeed, etc.)

    // CHANGED: Migrated to Volley + Offline Queue
    private fun toggleLike(post: Post, wantLike: Boolean) {
        val action = if (wantLike) "like" else "unlike"

        // 1. Create Payload
        val payload = JSONObject()
        payload.put("post_id", post.postId)
        payload.put("user_id", currentUid)
        payload.put("action", action)

        // 2. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "like_post.php" // (from ApiService.kt)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("home_page", "Like successful")
                            // We can optionally refresh the post from the DB
                        } else {
                            Log.w("home_page", "API error liking post: ${json.getString("message")}")
                        }
                    } catch (e: Exception) { Log.e("home_page", "Error parsing like response: ${e.message}") }
                },
                { error ->
                    Log.e("home_page", "Volley error liking: ${error.message}")
                    saveToSyncQueue("like_post.php", payload) // Save if network fails
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["post_id"] = post.postId
                    params["user_id"] = currentUid
                    params["action"] = action
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // 3. Save to queue if offline
            saveToSyncQueue("like_post.php", payload)
        }

        // 4. Optimistic UI update (the adapter already does this)
        Log.d("home_page", "Optimistic like update performed.")
    }

    // REMOVED: showAddCommentDialog (This just shows a dialog, can be kept)

    // CHANGED: Migrated to Volley + Offline Queue
    private fun addComment(post: Post, text: String) {
        // 1. Create Payload
        val payload = JSONObject()
        payload.put("post_id", post.postId)
        payload.put("user_id", currentUid)
        payload.put("text", text)

        // 2. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "add_comment.php" // (from ApiService.kt)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("home_page", "Comment posted")
                            // On success, add comment to local DB and refresh
                            val commentObj = json.getJSONObject("data")
                            val cv = ContentValues()
                            cv.put(DB.Comment.COLUMN_COMMENT_ID, commentObj.getString("commentId"))
                            cv.put(DB.Comment.COLUMN_POST_ID, commentObj.getString("postId"))
                            cv.put(DB.Comment.COLUMN_UID, commentObj.getString("uid"))
                            cv.put(DB.Comment.COLUMN_USERNAME, commentObj.getString("username"))
                            cv.put(DB.Comment.COLUMN_TEXT, commentObj.getString("text"))
                            cv.put(DB.Comment.COLUMN_CREATED_AT, commentObj.getLong("createdAt"))
                            dbHelper.writableDatabase.insert(DB.Comment.TABLE_NAME, null, cv)

                            // Refresh UI
                            loadCommentsForPostFromDb(post.postId)
                        } else {
                            Log.w("home_page", "API error adding comment: ${json.getString("message")}")
                        }
                    } catch (e: Exception) { Log.e("home_page", "Error parsing comment response: ${e.message}") }
                },
                { error ->
                    Log.e("home_page", "Volley error adding comment: ${error.message}")
                    saveToSyncQueue("add_comment.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["post_id"] = post.postId
                    params["user_id"] = currentUid
                    params["text"] = text
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // 3. Save to queue if offline
            saveToSyncQueue("add_comment.php", payload)
            // TODO: Optimistic UI - add comment to local DB with a "pending" status
        }
    }

    private fun decodeBase64ToBitmap(raw: String?): Bitmap? {
        // No changes needed
        if (raw.isNullOrBlank()) return null
        val clean = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    // CHANGED: Stubbed out for Agora
    private fun setupCallListener() {
        Log.d("home_page", "Setting up Agora call listener for user: $currentUid")
        // REMOVED: CallManager.listenForIncomingCalls(...)
        // TODO: Implement Agora listener logic here (Task #7)
        // This will likely involve a service or a socket connection
        // that your backend (Dev A) will trigger.
    }

    // CHANGED: Renamed from setupCallListener
    private fun setupAgoraCallListener() {
        // TODO: Implement Agora logic
        Log.d("home_page", "Agora listener setup pending...")
    }

    private fun showIncomingCall(callId: String, callerName: String, isVideoCall: Boolean) {
        // No changes needed, this logic is correct
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("CALLER_NAME", callerName)
            putExtra("IS_VIDEO_CALL", isVideoCall)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    // CHANGED: Migrated to save token to your backend via Volley
    private fun getFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("FCM", "Failed to get token", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM", "FCM Token: $token")

            if (currentUid.isEmpty()) return@addOnCompleteListener

            // --- CHANGED: Save token to your backend ---
            val url = AppGlobals.BASE_URL + "update_fcm_token.php" // (from ApiService.kt)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response -> Log.d("FCM", "FCM Token updated on server.") },
                { error -> Log.e("FCM", "Failed to update FCM token: ${error.message}") }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["user_id"] = currentUid
                    params["fcm_token"] = token
                    return params
                }
            }
            queue.add(stringRequest)
            // --- END OF CHANGE ---
        }
    }

    private fun requestNotificationPermission() {
        // No changes needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    // --- CHANGED: PostAdapter Inner Class ---
    inner class PostAdapter(
        private val currentUid: String,
        private val onLikeToggle: (post: Post, liked: Boolean) -> Unit,
        private val onCommentClick: (post: Post) -> Unit,
        private val onSendClick: (post: Post) -> Unit,
        private val onPostClick: (post: Post) -> Unit
    ) : RecyclerView.Adapter<PostAdapter.PostVH>() {

        // No changes to these variables
        private val items = mutableListOf<Post>()
        private val likeState = mutableMapOf<String, Boolean>()
        private val likeCounts = mutableMapOf<String, Int>()
        private val commentPreviews = mutableMapOf<String, List<Comment>>()
        private val commentTotals = mutableMapOf<String, Int>()

        fun submitList(list: List<Post>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun setLikeCount(postId: String, count: Int) {
            likeCounts[postId] = count
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        fun setLiked(postId: String, liked: Boolean) {
            likeState[postId] = liked
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        fun setCommentPreview(postId: String, comments: List<Comment>) {
            commentPreviews[postId] = comments
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        fun setCommentTotal(postId: String, total: Int) {
            commentTotals[postId] = total
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        inner class PostVH(v: View) : RecyclerView.ViewHolder(v) {
            // No changes needed
            val avatar: ImageView = v.findViewById(R.id.imgAvatar)
            val username: TextView = v.findViewById(R.id.tvUsername)
            val postImage: ImageView = v.findViewById(R.id.imgPost)
            val likeBtn: ImageView = v.findViewById(R.id.btnLike)
            val tvLikes: TextView = v.findViewById(R.id.tvLikes)
            val tvCaption: TextView = v.findViewById(R.id.tvCaption)
            val tvC1: TextView = v.findViewById(R.id.tvComment1)
            val tvC2: TextView = v.findViewById(R.id.tvComment2)
            val tvViewAll: TextView = v.findViewById(R.id.tvViewAll)
            val commentBtn: ImageView = v.findViewById(R.id.btnComment)
            val sendBtn: ImageView = v.findViewById(R.id.btnShare)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
            return PostVH(v)
        }

        @SuppressLint("RecyclerView", "SetTextI18n")
        override fun onBindViewHolder(h: PostVH, position: Int) {
            val item = items[position]

            // CHANGED: Simplified. Username should *always* come from the `item`
            // which was loaded from the DB (and populated by the API).
            h.username.text = item.username
            h.tvCaption.text = "${item.username}  ${item.caption}"

            // REMOVED: Firebase username fetch
            // REMOVED: h.avatar.loadUserAvatar(...)

            // CHANGED: Load avatar from local DB
            // This is still tricky. We need the post author's profile pic.
            // The API `get_feed.php` should include `userProfilePicture` in the Post object.
            // Assuming it does (and we saved it to `DB.Post`), we'd load it here.
            // Since `Post.kt` model doesn't have it, I'll load a placeholder.
            h.avatar.setImageResource(R.drawable.oval)

            // This image loading logic is fine
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(h.postImage.context)
                    .load(item.imageUrl) // Load URL from API
                    .placeholder(R.drawable.person1)
                    .error(R.drawable.person1)
                    .into(h.postImage)
            } else if (item.imageBase64.isNotEmpty()) {
                val bmp = decodeBase64ToBitmap(item.imageBase64)
                if (bmp != null) h.postImage.setImageBitmap(bmp) else h.postImage.setImageResource(R.drawable.person1)
            } else {
                h.postImage.setImageResource(R.drawable.person1)
            }

            // This like logic is fine (handles optimistic UI)
            val initialLikes = item.likeCount.toInt()
            val liked = likeState[item.postId] == true
            h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
            val liveCount = likeCounts[item.postId] ?: initialLikes
            h.tvLikes.text = if (liveCount == 1) "1 like" else "$liveCount likes"

            // This comment logic is fine
            val previews = commentPreviews[item.postId] ?: emptyList()
            if (previews.isNotEmpty()) {
                h.tvC1.visibility = View.VISIBLE
                h.tvC1.text = "${previews[0].username}: ${previews[0].text}"
            } else {
                h.tvC1.visibility = View.GONE
                h.tvC1.text = ""
            }
            if (previews.size >= 2) {
                h.tvC2.visibility = View.VISIBLE
                h.tvC2.text = "${previews[1].username}: ${previews[1].text}"
            } else {
                h.tvC2.visibility = View.GONE
                h.tvC2.text = ""
            }

            val total = commentTotals[item.postId] ?: item.commentCount.toInt()
            h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

            // This like click logic is fine (handles optimistic UI)
            h.likeBtn.setOnClickListener {
                val currentlyLiked = likeState[item.postId] == true
                val wantLike = !currentlyLiked

                likeState[item.postId] = wantLike
                val base = likeCounts[item.postId] ?: item.likeCount.toInt()
                val newCount = (base + if (wantLike) 1 else -1).coerceAtLeast(0)
                likeCounts[item.postId] = newCount
                h.likeBtn.setImageResource(if (wantLike) R.drawable.liked else R.drawable.like)
                h.tvLikes.text = if (newCount == 1) "1 like" else "$newCount likes"

                onLikeToggle(item, wantLike) // Triggers the network/offline call
            }

            // This click logic is fine
            h.postImage.setOnClickListener { onPostClick(item) }
            h.tvCaption.setOnClickListener { onPostClick(item) }
            h.commentBtn.setOnClickListener { onCommentClick(item) }
            h.tvViewAll.setOnClickListener { onCommentClick(item) }
            h.sendBtn.setOnClickListener { onSendClick(item) }
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }
    // --- END OF PostAdapter ---


    // --- CHANGED: HELPER FUNCTIONS FOR OFFLINE QUEUE & NETWORK ---

    /**
     * Saves a failed or offline request to the SQLite sync queue.
     */
    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("home_page", "Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            // Show toast on the UI thread
            runOnUiThread {
                Toast.makeText(this, "Offline. Action will sync later.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("home_page", "Failed to save to sync queue: ${e.message}")
        }
    }

    /**
     * Checks if the device is connected to the internet.
     */
    /**
     * Checks if the device is connected to the internet.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            // --- CORRECTED 'when' BLOCK ---
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true // Added this
                else -> false // Added this
            }
            // --- END OF CORRECTION ---
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}