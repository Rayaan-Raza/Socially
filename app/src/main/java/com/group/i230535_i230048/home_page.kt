package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
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
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject

class home_page : AppCompatActivity() {

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

    private var postToSend: Post? = null
    private val selectFriendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedUserId = result.data?.getStringExtra("SELECTED_USER_ID")
            if (selectedUserId != null && postToSend != null) {
                sendMessageWithPost(selectedUserId, postToSend!!)
            }
        }
    }

    fun loadBottomBarAvatar(navProfile: ImageView) {
        try {
            navProfile.loadUserAvatar(currentUid, currentUid, R.drawable.oval)
        } catch (e: Exception) {
            Log.e("home_page", "Error loading bottom bar avatar: ${e.message}")
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUid = prefs.getString(AppGlobals.KEY_USER_UID, null) ?: ""
        currentUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"

        if (currentUid.isEmpty()) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }

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
        storyAdapter = StoryAdapter(storyList, currentUid)
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
        getFcmToken()
        requestNotificationPermission()
    }

    private fun openPostDetail(post: Post, showComments: Boolean = false) {
        try {
            val intent = Intent(this, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", post.postId)
                putExtra("USER_ID", post.uid)
                putExtra("SHOW_COMMENTS", showComments)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("home_page", "Error opening post detail: ${e.message}")
            Toast.makeText(this, "Unable to open post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendPostToFriend(post: Post) {
        try {
            postToSend = post
            val intent = Intent(this, dms::class.java)
            intent.putExtra("ACTION_MODE", "SHARE")
            selectFriendLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("home_page", "Error sending post to friend: ${e.message}")
            Toast.makeText(this, "Unable to share post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessageWithPost(recipientId: String, post: Post) {
        val payload = JSONObject()
        payload.put("senderUid", currentUid)
        payload.put("receiverUid", recipientId)
        payload.put("messageType", "post")
        payload.put("content", "Shared a post")
        payload.put("postId", post.postId)

        if (isNetworkAvailable(this)) {
            val stringRequest = object : StringRequest(
                Request.Method.POST, AppGlobals.BASE_URL + "messages_send.php",
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Toast.makeText(this, "Post sent!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to send: ${json.optString("message", "Unknown error")}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("home_page", "Error parsing send_message: ${e.message}")
                    }
                },
                { error ->
                    Log.e("home_page", "Volley error send_message: ${error.message}")
                    saveToSyncQueue("messages_send.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["senderUid"] = currentUid
                    params["receiverUid"] = recipientId
                    params["messageType"] = "post"
                    params["content"] = "Shared a post"
                    params["postId"] = post.postId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("messages_send.php", payload)
        }
    }

    private fun setupClickListeners() {
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
        loadStoriesFromDb()
        loadFeedFromDb()
        fetchMyProfile()
        fetchStoriesFromApi()
        fetchFeedFromApi()
        setupAgoraCallListener()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun loadStoriesFromDb() {
        try {
            storyList.clear()

            // 1. Always add "Your Story" first
            storyList.add(StoryBubble(currentUid, "Your Story", null, false))

            // 2. Load others from DB
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                "story_bubbles",
                null, null, null, null, null, null
            )

            while (cursor.moveToNext()) {
                try {
                    val uid = cursor.getString(cursor.getColumnIndexOrThrow("uid"))

                    // --- FIX: Skip current user (prevents "Your Story" + "Username" duplicate) ---
                    if (uid == currentUid) continue

                    val username = cursor.getString(cursor.getColumnIndexOrThrow("username"))
                    val profileUrl = cursor.getString(cursor.getColumnIndexOrThrow("profileUrl"))
                    val hasUnseen = cursor.getInt(cursor.getColumnIndexOrThrow("hasUnseen")) == 1

                    storyList.add(StoryBubble(uid, username, profileUrl, hasUnseen))
                } catch (e: Exception) {
                    Log.e("home_page", "Error loading story from DB: ${e.message}")
                }
            }
            cursor.close()

            storyAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e("home_page", "Error in loadStoriesFromDb: ${e.message}")
            if (storyList.isEmpty()) {
                storyList.add(StoryBubble(currentUid, "Your Story", null, false))
                storyAdapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Helper to clean PHP errors/warnings from JSON response
     * Add this method to your home_page class
     */
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        // Check for PHP errors
        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Warning:")) {
            Log.e("home_page", "❌ PHP error in response")
            Log.e("home_page", "Raw response: ${cleaned.take(500)}")
            return "{\"success\": false, \"message\": \"Server error\"}"
        }

        // If response doesn't start with JSON, try to find it
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            Log.w("home_page", "Response doesn't start with JSON, trying to clean...")
            val jsonStart = cleaned.indexOf("{\"")
            if (jsonStart > 0) {
                val beforeJson = cleaned.substring(0, jsonStart)
                Log.e("home_page", "PHP output before JSON: $beforeJson")
                cleaned = cleaned.substring(jsonStart)
            } else if (jsonStart == -1) {
                Log.e("home_page", "No valid JSON found in response")
                return "{\"success\": false, \"message\": \"Invalid server response\"}"
            }
        }

        return cleaned
    }

    /**
     * Replace your existing fetchStoriesFromApi() with this version
     */
    private fun fetchStoriesFromApi() {
        Log.d("home_page", "📖 Fetching stories from API...")
        val url = AppGlobals.BASE_URL + "stories_bubbles_get.php?uid=$currentUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleaned = cleanJsonResponse(response)
                    val json = JSONObject(cleaned)

                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val bubbles = mutableListOf<StoryBubble>()

                        for (i in 0 until dataArray.length()) {
                            try {
                                val bubbleObj = dataArray.getJSONObject(i)
                                val uid = bubbleObj.optString("uid", "")

                                // --- FIX: Skip current user here too ---
                                if (uid == currentUid) continue

                                val bubble = StoryBubble(
                                    uid = uid,
                                    username = bubbleObj.optString("username", "User"),
                                    profileUrl = bubbleObj.optString("profileUrl", null),
                                    hasStories = bubbleObj.optBoolean("hasStories", false)
                                )
                                bubbles.add(bubble)
                            } catch (e: Exception) { }
                        }

                        storyList.clear()
                        // Add Your Story First
                        storyList.add(StoryBubble(currentUid, "Your Story", null, false))
                        // Add others
                        storyList.addAll(bubbles)

                        storyAdapter.notifyDataSetChanged()

                        // Save to DB (only others)
                        saveStoriesToDb(bubbles)
                    }
                } catch (e: Exception) {
                    Log.e("home_page", "❌ Error parsing stories: ${e.message}")
                }
            },
            { error -> Log.e("home_page", "❌ Volley error fetching stories: ${error.message}") }
        )
        queue.add(stringRequest)
    }
    private fun saveStoriesToDb(bubbles: List<StoryBubble>) {
        try {
            val db = dbHelper.writableDatabase
            db.delete("story_bubbles", null, null)

            for (bubble in bubbles) {
                val cv = ContentValues()
                cv.put("uid", bubble.uid)
                cv.put("username", bubble.username)
                cv.put("profileUrl", bubble.profileUrl)
                cv.put("hasUnseen", if (bubble.hasStories) 1 else 0)
                db.insert("story_bubbles", null, cv)
            }
        } catch (e: Exception) {
            Log.e("home_page", "Error saving stories to DB: ${e.message}")
        }
    }

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
            holder.binding.username.text = item.username ?: "User"

            try {
                if (item.uid == currentUid) {
                    // For "Your Story", load current user's avatar
                    holder.binding.pfp.loadUserAvatar(currentUid, currentUid, R.drawable.person1)
                } else {
                    // For others, load their avatar
                    holder.binding.pfp.loadUserAvatar(item.uid, currentUid, R.drawable.person1)
                }
            } catch (e: Exception) {
                Log.e("StoryAdapter", "Error loading avatar: ${e.message}")
                holder.binding.pfp.setImageResource(R.drawable.person1)
            }

            holder.binding.root.setOnClickListener {
                try {
                    val intent = Intent(this@home_page, camera_story::class.java)
                    intent.putExtra("uid", item.uid)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("StoryAdapter", "Error opening story: ${e.message}")
                    Toast.makeText(this@home_page, "Unable to open story", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun loadFeedFromDb() {
        Log.d("home_page", "Loading feed from local DB...")
        try {
            val db = dbHelper.readableDatabase
            currentPosts.clear()

            val postCursor = db.query(
                DB.Post.TABLE_NAME, null, null, null, null, null,
                DB.Post.COLUMN_CREATED_AT + " DESC"
            )

            while (postCursor.moveToNext()) {
                try {
                    // Check if iLiked column exists
                    var iLiked = false
                    try {
                        val iLikedIndex = postCursor.getColumnIndex(DB.Post.COLUMN_I_LIKED)
                        if (iLikedIndex >= 0) {
                            iLiked = postCursor.getInt(iLikedIndex) == 1
                        }
                    } catch (e: Exception) {
                        Log.w("home_page", "iLiked column not found, using default: ${e.message}")
                    }

                    val post = Post(
                        postId = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)) ?: "",
                        uid = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)) ?: "",
                        username = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)) ?: "user",
                        caption = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)) ?: "",
                        imageUrl = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)) ?: "",
                        imageBase64 = postCursor.getString(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)) ?: "",
                        createdAt = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                        likeCount = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                        commentCount = postCursor.getLong(postCursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT)),
                        iLiked = iLiked  // Pass it in the constructor
                    )

                    currentPosts.add(post)
                } catch (e: Exception) {
                    Log.e("home_page", "Error parsing post from DB: ${e.message}")
                }
            }
            postCursor.close()

            postAdapter.submitList(currentPosts.toList())

            currentPosts.forEach { post ->
                loadCommentsForPostFromDb(post.postId)
            }
        } catch (e: Exception) {
            Log.e("home_page", "Error in loadFeedFromDb: ${e.message}")
            postAdapter.submitList(emptyList())
        }
    }

    private fun loadCommentsForPostFromDb(postId: String) {
        try {
            val db = dbHelper.readableDatabase
            val comments = mutableListOf<Comment>()

            val commentCursor = db.query(
                DB.Comment.TABLE_NAME, null,
                "${DB.Comment.COLUMN_POST_ID} = ?", arrayOf(postId),
                null, null, DB.Comment.COLUMN_CREATED_AT + " DESC", "2"
            )

            while(commentCursor.moveToNext()) {
                try {
                    comments.add(
                        Comment(
                            commentId = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_COMMENT_ID)) ?: "",
                            postId = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_POST_ID)) ?: "",
                            uid = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_UID)) ?: "",
                            username = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_USERNAME)) ?: "user",
                            text = commentCursor.getString(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_TEXT)) ?: "",
                            createdAt = commentCursor.getLong(commentCursor.getColumnIndexOrThrow(DB.Comment.COLUMN_CREATED_AT))
                        )
                    )
                } catch (e: Exception) {
                    Log.e("home_page", "Error parsing comment: ${e.message}")
                }
            }
            commentCursor.close()

            postAdapter.setCommentPreview(postId, comments.reversed())
        } catch (e: Exception) {
            Log.e("home_page", "Error loading comments for post: ${e.message}")
        }
    }

    private fun fetchFeedFromApi() {
        Log.d("home_page", "Fetching feed from API...")
        val url = AppGlobals.BASE_URL + "feed.php?uid=$currentUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val db = dbHelper.writableDatabase

                        val listType = object : TypeToken<List<Post>>() {}.type
                        val newPosts: List<Post> = Gson().fromJson(dataArray.toString(), listType)

                        db.beginTransaction()
                        try {
                            db.delete(DB.Post.TABLE_NAME, null, null)
                            db.delete(DB.Comment.TABLE_NAME, null, null)

                            for (post in newPosts) {
                                // Save the Post
                                val cvPost = ContentValues()
                                cvPost.put(DB.Post.COLUMN_POST_ID, post.postId)
                                cvPost.put(DB.Post.COLUMN_UID, post.uid)
                                cvPost.put(DB.Post.COLUMN_USERNAME, post.username)
                                cvPost.put(DB.Post.COLUMN_CAPTION, post.caption)
                                cvPost.put(DB.Post.COLUMN_IMAGE_URL, post.imageUrl)
                                cvPost.put(DB.Post.COLUMN_IMAGE_BASE64, post.imageBase64)
                                cvPost.put(DB.Post.COLUMN_CREATED_AT, post.createdAt)
                                cvPost.put(DB.Post.COLUMN_LIKE_COUNT, post.likeCount)
                                cvPost.put(DB.Post.COLUMN_COMMENT_COUNT, post.commentCount)

                                // Save iLiked if column exists
//                                try {
//                                    cvPost.put(DB.Post.COLUMN_I_LIKED, if (post.iLiked) 1 else 0)
//                                } catch (e: Exception) {
//                                    Log.w("home_page", "iLiked column not in schema: ${e.message}")
//                                }

                                db.insert(DB.Post.TABLE_NAME, null, cvPost)

                                // Save the latest comments
                                val comments = post.latestComments ?: emptyList()
                                for (comment in comments) {
                                    val cvComment = ContentValues()
                                    cvComment.put(DB.Comment.COLUMN_COMMENT_ID, comment.commentId)
                                    cvComment.put(DB.Comment.COLUMN_POST_ID, comment.postId)
                                    cvComment.put(DB.Comment.COLUMN_UID, comment.uid)
                                    cvComment.put(DB.Comment.COLUMN_USERNAME, comment.username)
                                    cvComment.put(DB.Comment.COLUMN_TEXT, comment.text)
                                    cvComment.put(DB.Comment.COLUMN_CREATED_AT, comment.createdAt)
                                    db.insert(DB.Comment.TABLE_NAME, null, cvComment)
                                }
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }

                        loadFeedFromDb()
                    }
                } catch (e: Exception) {
                    Log.e("home_page", "Error parsing feed: ${e.message}")
                }
            },
            { error -> Log.w("home_page", "Volley error fetching feed: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun fetchMyProfile() {
        val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$currentUid"
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")
                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.optString("uid", currentUid))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.optString("username", currentUsername))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.optString("fullName", ""))
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, userObj.optString("profilePictureUrl", ""))
                        cv.put(DB.User.COLUMN_EMAIL, userObj.optString("email", ""))
                        cv.put(DB.User.COLUMN_BIO, userObj.optString("bio", ""))

                        val db = dbHelper.writableDatabase
                        db.insertWithOnConflict(DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        loadBottomBarAvatar(navProfileImage)
                    }
                } catch (e: Exception) {
                    Log.e("home_page", "Error parsing my profile: ${e.message}")
                }
            },
            { error -> Log.w("home_page", "Volley error fetching profile: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun toggleLike(post: Post, wantLike: Boolean) {
        val payload = JSONObject()
        payload.put("postId", post.postId)
        payload.put("uid", currentUid)
        payload.put("liked", if (wantLike) "1" else "0")

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "posts_like.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("home_page", "Like successful")
                            val newCount = json.getJSONObject("data").optInt("likeCount", 0)
                            postAdapter.setLikeCount(post.postId, newCount)
                        } else {
                            Log.w("home_page", "API error liking post: ${json.optString("message", "Unknown error")}")
                        }
                    } catch (e: Exception) {
                        Log.e("home_page", "Error parsing like response: ${e.message}")
                    }
                },
                { error ->
                    Log.e("home_page", "Volley error liking: ${error.message}")
                    saveToSyncQueue("posts_like.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["postId"] = post.postId
                    params["uid"] = currentUid
                    params["liked"] = if (wantLike) "1" else "0"
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("posts_like.php", payload)
        }
    }

    private fun addComment(post: Post, text: String) {
        val payload = JSONObject()
        payload.put("postId", post.postId)
        payload.put("uid", currentUid)
        payload.put("text", text)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "posts_comment_add.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("home_page", "Comment posted")
                            val commentObj = json.getJSONObject("data")
                            val cv = ContentValues()
                            cv.put(DB.Comment.COLUMN_COMMENT_ID, commentObj.optString("commentId", ""))
                            cv.put(DB.Comment.COLUMN_POST_ID, commentObj.optString("postId", ""))
                            cv.put(DB.Comment.COLUMN_UID, commentObj.optString("uid", ""))
                            cv.put(DB.Comment.COLUMN_USERNAME, commentObj.optString("username", ""))
                            cv.put(DB.Comment.COLUMN_TEXT, commentObj.optString("text", ""))
                            cv.put(DB.Comment.COLUMN_CREATED_AT, commentObj.optLong("createdAt", System.currentTimeMillis()))
                            dbHelper.writableDatabase.insert(DB.Comment.TABLE_NAME, null, cv)

                            loadCommentsForPostFromDb(post.postId)
                        } else {
                            Log.w("home_page", "API error adding comment: ${json.optString("message", "Unknown error")}")
                        }
                    } catch (e: Exception) {
                        Log.e("home_page", "Error parsing comment response: ${e.message}")
                    }
                },
                { error ->
                    Log.e("home_page", "Volley error adding comment: ${error.message}")
                    saveToSyncQueue("posts_comment_add.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["postId"] = post.postId
                    params["uid"] = currentUid
                    params["text"] = text
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("posts_comment_add.php", payload)
        }
    }

    private fun decodeBase64ToBitmap(raw: String?): Bitmap? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("home_page", "Error decoding base64: ${e.message}")
            null
        }
    }

    private fun setupAgoraCallListener() {
        try {
            // Placeholder for Agora call listener
            Log.d("home_page", "Setting up Agora call listener for user: $currentUid")

            // If you have CallManager implemented:
            // CallManager.listenForIncomingCalls(currentUid) { callId, callerName, isVideoCall ->
            //     showIncomingCall(callId, callerName, isVideoCall)
            // }
        } catch (e: Exception) {
            Log.e("home_page", "Error setting up call listener: ${e.message}")
        }
    }

//    private fun showIncomingCall(callId: String, callerName: String, isVideoCall: Boolean) {
//        try {
//            val intent = Intent(this, IncomingCallActivity::class.java).apply {
//                putExtra("CALL_ID", callId)
//                putExtra("CALLER_NAME", callerName)
//                putExtra("IS_VIDEO_CALL", isVideoCall)
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
//            }
//            startActivity(intent)
//        } catch (e: Exception) {
//            Log.e("home_page", "Error showing incoming call: ${e.message}")
//            Toast.makeText(this, "Unable to handle incoming call", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun getFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("FCM", "Failed to get token", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (currentUid.isEmpty()) return@addOnCompleteListener

                val url = AppGlobals.BASE_URL + "user_update_fcm.php"
                val stringRequest = object : StringRequest(Request.Method.POST, url,
                    { Log.d("FCM", "FCM Token updated on server.") },
                    { error -> Log.e("FCM", "Failed to update FCM token: ${error.message}") }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params["uid"] = currentUid
                        params["fcmToken"] = token
                        return params
                    }
                }
                queue.add(stringRequest)
            }
        } catch (e: Exception) {
            Log.e("home_page", "Error getting FCM token: ${e.message}")
        }
    }

    private fun requestNotificationPermission() {
        try {
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
        } catch (e: Exception) {
            Log.e("home_page", "Error requesting notification permission: ${e.message}")
        }
    }

    inner class PostAdapter(
        private val currentUid: String,
        private val onLikeToggle: (post: Post, liked: Boolean) -> Unit,
        private val onCommentClick: (post: Post) -> Unit,
        private val onSendClick: (post: Post) -> Unit,
        private val onPostClick: (post: Post) -> Unit
    ) : RecyclerView.Adapter<PostAdapter.PostVH>() {

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

            try {
                // Set username with fallback
                val displayUsername = if (item.username.isNotBlank()) {
                    item.username
                } else {
                    usernameCache[item.uid] ?: "user"
                }
                h.username.text = displayUsername
                h.tvCaption.text = "$displayUsername  ${item.caption}"

                // Load avatar with error handling
                try {
                    h.avatar.loadUserAvatar(item.uid, currentUid, R.drawable.oval)
                } catch (e: Exception) {
                    Log.e("PostAdapter", "Error loading avatar: ${e.message}")
                    h.avatar.setImageResource(R.drawable.oval)
                }

                // Load post image with multiple fallbacks
                try {
                    if (item.imageUrl.isNotEmpty()) {
                        Glide.with(h.postImage.context)
                            .load(item.imageUrl)
                            .placeholder(R.drawable.person1)
                            .error(R.drawable.person1)
                            .into(h.postImage)
                    } else if (item.imageBase64.isNotEmpty()) {
                        val bmp = decodeBase64ToBitmap(item.imageBase64)
                        if (bmp != null) {
                            h.postImage.setImageBitmap(bmp)
                        } else {
                            h.postImage.setImageResource(R.drawable.person1)
                        }
                    } else {
                        h.postImage.setImageResource(R.drawable.person1)
                    }
                } catch (e: Exception) {
                    Log.e("PostAdapter", "Error loading post image: ${e.message}")
                    h.postImage.setImageResource(R.drawable.person1)
                }

                // Handle like state with fallbacks
                val liked = likeState[item.postId] ?: try {
                    item.iLiked
                } catch (e: Exception) {
                    false
                }

                val liveCount = likeCounts[item.postId] ?: item.likeCount.toInt()
                h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
                h.tvLikes.text = if (liveCount == 1) "1 like" else "$liveCount likes"

                // Handle comment previews with fallbacks
                val previews = commentPreviews[item.postId] ?: try {
                    item.latestComments ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                if (previews.isNotEmpty()) {
                    h.tvC1.visibility = View.VISIBLE
                    h.tvC1.text = "${previews[0].username}: ${previews[0].text}"
                } else {
                    h.tvC1.visibility = View.GONE
                }

                if (previews.size >= 2) {
                    h.tvC2.visibility = View.VISIBLE
                    h.tvC2.text = "${previews[1].username}: ${previews[1].text}"
                } else {
                    h.tvC2.visibility = View.GONE
                }

                val total = commentTotals[item.postId] ?: item.commentCount.toInt()
                h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

                // Set click listeners with error handling
                h.likeBtn.setOnClickListener {
                    try {
                        val currentlyLiked = likeState[item.postId] ?: try {
                            item.iLiked
                        } catch (e: Exception) {
                            false
                        }
                        val wantLike = !currentlyLiked

                        likeState[item.postId] = wantLike
                        val base = likeCounts[item.postId] ?: item.likeCount.toInt()
                        val newCount = (base + if (wantLike) 1 else -1).coerceAtLeast(0)
                        likeCounts[item.postId] = newCount
                        h.likeBtn.setImageResource(if (wantLike) R.drawable.liked else R.drawable.like)
                        h.tvLikes.text = if (newCount == 1) "1 like" else "$newCount likes"

                        onLikeToggle(item, wantLike)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error toggling like: ${e.message}")
                    }
                }

                h.postImage.setOnClickListener {
                    try {
                        onPostClick(item)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error on post click: ${e.message}")
                    }
                }

                h.tvCaption.setOnClickListener {
                    try {
                        onPostClick(item)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error on caption click: ${e.message}")
                    }
                }

                h.commentBtn.setOnClickListener {
                    try {
                        onCommentClick(item)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error on comment click: ${e.message}")
                    }
                }

                h.tvViewAll.setOnClickListener {
                    try {
                        onCommentClick(item)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error on view all click: ${e.message}")
                    }
                }

                h.sendBtn.setOnClickListener {
                    try {
                        onSendClick(item)
                    } catch (e: Exception) {
                        Log.e("PostAdapter", "Error on send click: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e("PostAdapter", "Error binding post at position $position: ${e.message}")
                // Set safe defaults
                h.username.text = "user"
                h.tvCaption.text = "Post unavailable"
                h.avatar.setImageResource(R.drawable.oval)
                h.postImage.setImageResource(R.drawable.person1)
                h.tvLikes.text = "0 likes"
                h.tvC1.visibility = View.GONE
                h.tvC2.visibility = View.GONE
                h.tvViewAll.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("home_page", "Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            runOnUiThread {
                Toast.makeText(this, "Offline. Action will sync later.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("home_page", "Failed to save to sync queue: ${e.message}")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        } catch (e: Exception) {
            Log.e("home_page", "Error checking network: ${e.message}")
            false
        }
    }
}