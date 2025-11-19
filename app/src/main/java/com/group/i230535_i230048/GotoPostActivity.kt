package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.group.i230535_i230048.AppDbHelper
import com.group.i230535_i230048.DB
import org.json.JSONObject
import java.util.UUID

class GotoPostActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GotoPostActivity"
    }

    // --- Local DB, Volley, and Session ---
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var myUid: String = ""
    private var myUsername: String = ""
    // ---

    private var postId: String? = null
    private var postUserId: String? = null
    private var currentPost: Post? = null

    // Views
    private lateinit var imgAvatar: ImageView
    private lateinit var tvUsername: TextView
    private lateinit var imgPost: ImageView
    private lateinit var btnLike: ImageView
    private lateinit var btnShare: ImageView
    private lateinit var tvLikes: TextView
    private lateinit var tvCaption: TextView
    private lateinit var rvComments: RecyclerView
    private lateinit var etCommentInput: EditText
    private lateinit var btnPostComment: TextView
    private lateinit var backButton: ImageView
    private lateinit var progressBar: ProgressBar

    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    private val selectFriendLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedUserId = result.data?.getStringExtra("SELECTED_USER_ID")
            if (selectedUserId != null && currentPost != null) {
                sendMessageWithPost(selectedUserId, currentPost!!)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting GotoPostActivity")

        try {
            enableEdgeToEdge()
            setContentView(R.layout.activity_goto_post)

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
                findViewById<View>(R.id.comment_input_bar)?.setPadding(0, 0, 0, systemBars.bottom)
                insets
            }

            // --- Setup DB, Volley, and Session ---
            dbHelper = AppDbHelper(this)
            queue = Volley.newRequestQueue(this)

            val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            myUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
            myUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"

            Log.d(TAG, "onCreate: User session - myUid=$myUid, myUsername=$myUsername")

            // --- Get Intent Data ---
            postId = intent.getStringExtra("POST_ID")
            postUserId = intent.getStringExtra("USER_ID")

            Log.d(TAG, "onCreate: Intent data - postId=$postId, postUserId=$postUserId")

            // --- Validation ---
            if (myUid.isEmpty()) {
                Log.e(TAG, "onCreate: myUid is empty, finishing activity")
                Toast.makeText(this, "Error: Not logged in", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (postId.isNullOrEmpty()) {
                Log.e(TAG, "onCreate: postId is null or empty, finishing activity")
                Toast.makeText(this, "Error: Post ID missing", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (postUserId.isNullOrEmpty()) {
                Log.e(TAG, "onCreate: postUserId is null or empty, finishing activity")
                Toast.makeText(this, "Error: Post owner ID missing", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            initializeViews()
            setupRecyclerView()
            setupClickListeners()

            // --- Loading flow with better error handling ---
            Log.d(TAG, "onCreate: Starting data load")
            showLoading(true)

            val hasLocalData = loadPostDataFromDb()
            if (hasLocalData) {
                Log.d(TAG, "onCreate: Local data loaded successfully")
                showLoading(false)
            } else {
                Log.w(TAG, "onCreate: No local data found, will wait for API")
            }

            // Always fetch from API to get latest data
            fetchPostDetailsFromApi()

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Exception caught", e)
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeViews() {
        Log.d(TAG, "initializeViews: Starting")
        try {
            imgAvatar = findViewById(R.id.imgAvatar)
            tvUsername = findViewById(R.id.tvUsername)
            imgPost = findViewById(R.id.imgPost)
            btnLike = findViewById(R.id.btnLike)
            btnShare = findViewById(R.id.btnShare)
            tvLikes = findViewById(R.id.tvLikes)
            tvCaption = findViewById(R.id.tvCaption)
            rvComments = findViewById(R.id.rvComments)
            etCommentInput = findViewById(R.id.etCommentInput)
            btnPostComment = findViewById(R.id.btnPostComment)
            backButton = findViewById(R.id.backButton)

            Log.d(TAG, "initializeViews: All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initializeViews: Error finding views", e)
            throw e
        }
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Starting")
        try {
            commentAdapter = CommentAdapter(commentList, myUid)
            rvComments.layoutManager = LinearLayoutManager(this)
            rvComments.adapter = commentAdapter
            Log.d(TAG, "setupRecyclerView: RecyclerView setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "setupRecyclerView: Error", e)
        }
    }

    private fun setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Starting")
        try {
            backButton.setOnClickListener {
                Log.d(TAG, "Back button clicked")
                finish()
            }
            btnLike.setOnClickListener {
                Log.d(TAG, "Like button clicked")
                toggleLike()
            }
            btnShare.setOnClickListener {
                Log.d(TAG, "Share button clicked")
                sharePost()
            }
            btnPostComment.setOnClickListener {
                Log.d(TAG, "Post comment button clicked")
                postComment()
            }
            Log.d(TAG, "setupClickListeners: All listeners setup")
        } catch (e: Exception) {
            Log.e(TAG, "setupClickListeners: Error", e)
        }
    }

    private fun showLoading(show: Boolean) {
        try {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            Log.d(TAG, "showLoading: Loading indicator ${if (show) "shown" else "hidden"}")
        } catch (e: Exception) {
            Log.e(TAG, "showLoading: Error", e)
        }
    }

    // --- Load data from local SQLite DB ---
    // Returns true if data was found and loaded
    private fun loadPostDataFromDb(): Boolean {
        Log.d(TAG, "loadPostDataFromDb: Starting for postId=$postId")
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.Post.TABLE_NAME, null,
                "${DB.Post.COLUMN_POST_ID} = ?", arrayOf(postId),
                null, null, null
            )

            var dataFound = false
            if (cursor.moveToFirst()) {
                try {
                    val post = Post(
                        postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                        uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                        username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)),
                        caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)),
                        imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)) ?: "",
                        imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)) ?: "",
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                        likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                        commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                    )
                    currentPost = post
                    populatePostViews(post)
                    dataFound = true
                    Log.d(TAG, "loadPostDataFromDb: Successfully loaded post from DB")
                } catch (e: Exception) {
                    Log.e(TAG, "loadPostDataFromDb: Error parsing cursor data", e)
                }
            } else {
                Log.w(TAG, "loadPostDataFromDb: No post found in DB for postId=$postId")
            }
            cursor.close()

            // Also load comments from DB
            loadCommentsFromDb()

            return dataFound
        } catch (e: Exception) {
            Log.e(TAG, "loadPostDataFromDb: Exception", e)
            return false
        }
    }

    // --- Populate UI with Post object ---
    @SuppressLint("SetTextI18n")
    private fun populatePostViews(post: Post) {
        Log.d(TAG, "populatePostViews: Populating UI for postId=${post.postId}")
        try {
            runOnUiThread {
                try {
                    tvUsername.text = post.username.takeIf { it.isNotBlank() } ?: "user"
                    tvCaption.text = "${post.username}  ${post.caption}"

                    // Load avatar
                    try {
                        imgAvatar.loadUserAvatar(post.uid, myUid, R.drawable.oval)
                    } catch (e: Exception) {
                        Log.e(TAG, "populatePostViews: Error loading avatar", e)
                        imgAvatar.setImageResource(R.drawable.oval)
                    }

                    // Load post image
                    if (post.imageUrl.isNotEmpty()) {
                        Log.d(TAG, "populatePostViews: Loading image from URL")
                        Glide.with(this@GotoPostActivity)
                            .load(post.imageUrl)
                            .placeholder(R.drawable.person1)
                            .error(R.drawable.person1)
                            .into(imgPost)
                    } else if (post.imageBase64.isNotEmpty()) {
                        Log.d(TAG, "populatePostViews: Loading image from Base64")
                        val bitmap = decodeBase64ToBitmap(post.imageBase64)
                        if (bitmap != null) {
                            imgPost.setImageBitmap(bitmap)
                        } else {
                            Log.w(TAG, "populatePostViews: Failed to decode Base64 image")
                            imgPost.setImageResource(R.drawable.person1)
                        }
                    } else {
                        Log.w(TAG, "populatePostViews: No image URL or Base64 data")
                        imgPost.setImageResource(R.drawable.person1)
                    }

                    // Update likes
                    updateLikeDisplay(post.likeCount.toInt())

                    Log.d(TAG, "populatePostViews: UI populated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "populatePostViews: Error in UI thread", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "populatePostViews: Exception", e)
        }
    }

    // --- Fetch post details from API ---
    private fun fetchPostDetailsFromApi() {
        Log.d(TAG, "fetchPostDetailsFromApi: Starting API call for postId=$postId")

        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "fetchPostDetailsFromApi: No network available")
            showLoading(false)
            if (currentPost == null) {
                Toast.makeText(this, "No network connection. Cannot load post.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val url = "${AppGlobals.BASE_URL}post_get.php?postId=$postId"
        Log.d(TAG, "fetchPostDetailsFromApi: URL=$url")

        val stringRequest = object : StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchPostDetailsFromApi: Response received")
                Log.d(TAG, "fetchPostDetailsFromApi: Response body: $response")
                showLoading(false)

                try {
                    val json = JSONObject(response)
                    val success = json.optBoolean("success", false)

                    if (success) {
                        val data = json.getJSONObject("data")
                        Log.d(TAG, "fetchPostDetailsFromApi: Parsing post data")

                        val post = Post(
                            postId = data.optString("postId", postId ?: ""),
                            uid = data.optString("uid", postUserId ?: ""),
                            username = data.optString("username", "user"),
                            caption = data.optString("caption", ""),
                            imageUrl = data.optString("imageUrl", ""),
                            imageBase64 = data.optString("imageBase64", ""),
                            createdAt = data.optLong("createdAt", 0L),
                            likeCount = data.optLong("likeCount", 0L),
                            commentCount = data.optLong("commentCount", 0L)
                        )

                        currentPost = post
                        savePostToDb(post)
                        populatePostViews(post)

                        // Also fetch comments
                        fetchCommentsFromApi()

                        Log.d(TAG, "fetchPostDetailsFromApi: Post loaded successfully")
                    } else {
                        val message = json.optString("message", "Unknown error")
                        Log.e(TAG, "fetchPostDetailsFromApi: API returned success=false, message=$message")

                        if (currentPost == null) {
                            Toast.makeText(this, "Failed to load post: $message", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchPostDetailsFromApi: Error parsing response", e)
                    if (currentPost == null) {
                        Toast.makeText(this, "Error parsing post data: ${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            },
            { error ->
                Log.e(TAG, "fetchPostDetailsFromApi: Volley error", error)
                showLoading(false)

                if (currentPost == null) {
                    Toast.makeText(this, "Network error. Cannot load post.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, "Using cached data", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                return headers
            }
        }

        queue.add(stringRequest)
    }

    // --- Save post to local DB ---
    private fun savePostToDb(post: Post) {
        Log.d(TAG, "savePostToDb: Saving postId=${post.postId}")
        try {
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
            dbHelper.writableDatabase.insertWithOnConflict(
                DB.Post.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE
            )
            Log.d(TAG, "savePostToDb: Post saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "savePostToDb: Error saving to DB", e)
        }
    }

    // --- Load comments from DB ---
    private fun loadCommentsFromDb() {
        Log.d(TAG, "loadCommentsFromDb: Loading comments for postId=$postId")
        try {
            commentList.clear()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.Comment.TABLE_NAME, null,
                "${DB.Comment.COLUMN_POST_ID} = ?", arrayOf(postId),
                null, null, "${DB.Comment.COLUMN_CREATED_AT} ASC"
            )

            while (cursor.moveToNext()) {
                try {
                    val comment = Comment(
                        commentId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_COMMENT_ID)),
                        postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_POST_ID)),
                        uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_UID)),
                        username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_USERNAME)),
                        text = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_TEXT)),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_CREATED_AT))
                    )
                    commentList.add(comment)
                } catch (e: Exception) {
                    Log.e(TAG, "loadCommentsFromDb: Error parsing comment", e)
                }
            }
            cursor.close()

            runOnUiThread {
                commentAdapter.notifyDataSetChanged()
            }

            Log.d(TAG, "loadCommentsFromDb: Loaded ${commentList.size} comments")
        } catch (e: Exception) {
            Log.e(TAG, "loadCommentsFromDb: Exception", e)
        }
    }

    // --- Fetch comments from API ---
    private fun fetchCommentsFromApi() {
        Log.d(TAG, "fetchCommentsFromApi: Starting for postId=$postId")

        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "fetchCommentsFromApi: No network available")
            return
        }

        val url = "${AppGlobals.BASE_URL}post_comments_get.php?postId=$postId"
        Log.d(TAG, "fetchCommentsFromApi: URL=$url")

        val stringRequest = object : StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchCommentsFromApi: Response received")
                try {
                    val json = JSONObject(response)
                    if (json.optBoolean("success", false)) {
                        val commentsArray = json.optJSONArray("data")
                        if (commentsArray != null) {
                            Log.d(TAG, "fetchCommentsFromApi: Processing ${commentsArray.length()} comments")

                            // Clear existing comments in DB for this post
                            dbHelper.writableDatabase.delete(
                                DB.Comment.TABLE_NAME,
                                "${DB.Comment.COLUMN_POST_ID} = ?",
                                arrayOf(postId)
                            )

                            // Save new comments
                            for (i in 0 until commentsArray.length()) {
                                val commentObj = commentsArray.getJSONObject(i)
                                saveCommentToDb(
                                    commentObj.optString("commentId", ""),
                                    commentObj.optString("postId", postId ?: ""),
                                    commentObj.optString("uid", ""),
                                    commentObj.optString("username", "user"),
                                    commentObj.optString("text", ""),
                                    commentObj.optLong("createdAt", 0L)
                                )
                            }

                            // Reload from DB to update UI
                            loadCommentsFromDb()
                            Log.d(TAG, "fetchCommentsFromApi: Comments loaded successfully")
                        }
                    } else {
                        Log.w(TAG, "fetchCommentsFromApi: API returned success=false")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchCommentsFromApi: Error parsing response", e)
                }
            },
            { error ->
                Log.e(TAG, "fetchCommentsFromApi: Volley error", error)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                return headers
            }
        }

        queue.add(stringRequest)
    }

    // --- Toggle Like ---
    private fun toggleLike() {
        Log.d(TAG, "toggleLike: Starting")

        val post = currentPost
        if (post == null) {
            Log.e(TAG, "toggleLike: currentPost is null")
            Toast.makeText(this, "Post not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        btnLike.isEnabled = false

        // Optimistic UI update
        val currentLikes = post.likeCount.toInt()
        val newLikes = currentLikes + 1 // Simplified - in production track like state
        updateLikeDisplay(newLikes)

        if (!isNetworkAvailable(this)) {
            Log.w(TAG, "toggleLike: No network, saving to sync queue")
            val payload = JSONObject().apply {
                put("postId", post.postId)
                put("uid", myUid)
            }
            saveToSyncQueue("posts_like.php", payload)
            btnLike.isEnabled = true
            return
        }

        val url = "${AppGlobals.BASE_URL}posts_like.php"
        Log.d(TAG, "toggleLike: URL=$url")

        val stringRequest = object : StringRequest(
            Request.Method.POST, url,
            { response ->
                Log.d(TAG, "toggleLike: Response received: $response")
                try {
                    val json = JSONObject(response)
                    if (json.optBoolean("success", false)) {
                        val data = json.optJSONObject("data")
                        val likeCount = data?.optInt("likeCount", newLikes) ?: newLikes
                        updateLikeDisplay(likeCount)

                        // Update in DB
                        val updatedPost = post.copy(likeCount = likeCount.toLong())
                        currentPost = updatedPost
                        savePostToDb(updatedPost)

                        Log.d(TAG, "toggleLike: Like toggled successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "toggleLike: Error parsing response", e)
                }
                btnLike.isEnabled = true
            },
            { error ->
                Log.e(TAG, "toggleLike: Volley error", error)
                btnLike.isEnabled = true
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf(
                    "postId" to post.postId,
                    "uid" to myUid
                )
            }
        }

        queue.add(stringRequest)
    }

    private fun updateLikeDisplay(count: Int) {
        runOnUiThread {
            tvLikes.text = if (count == 1) "1 like" else "$count likes"
            Log.d(TAG, "updateLikeDisplay: Updated to $count likes")
        }
    }

    // --- Share Post ---
    private fun sharePost() {
        Log.d(TAG, "sharePost: Starting")

        if (currentPost == null) {
            Log.e(TAG, "sharePost: currentPost is null")
            Toast.makeText(this, "Post not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(this, SelectFriendActivity::class.java)
            selectFriendLauncher.launch(intent)
            Log.d(TAG, "sharePost: Launched SelectFriendActivity")
        } catch (e: Exception) {
            Log.e(TAG, "sharePost: Error launching SelectFriendActivity", e)
            Toast.makeText(this, "Error sharing post: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Send Message with Post ---
    private fun sendMessageWithPost(recipientUid: String, post: Post) {
        Log.d(TAG, "sendMessageWithPost: Sending to $recipientUid")

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("senderId", myUid)
            put("receiverId", recipientUid)
            put("messageType", "post")
            put("content", "")
            put("postId", post.postId)
            put("imageUrl", post.imageUrl.ifEmpty { post.imageBase64 })
            put("timestamp", timestamp)
        }

        if (isNetworkAvailable(this)) {
            val url = "${AppGlobals.BASE_URL}messages_send.php"
            Log.d(TAG, "sendMessageWithPost: Sending to API, URL=$url")

            val stringRequest = object : StringRequest(
                Request.Method.POST, url,
                { response ->
                    Log.d(TAG, "sendMessageWithPost: Response: $response")
                    try {
                        val json = JSONObject(response)
                        if (json.optBoolean("success", false)) {
                            Toast.makeText(this, "Post shared successfully!", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "sendMessageWithPost: Post shared successfully")
                        } else {
                            Toast.makeText(this, "Failed to share post", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "sendMessageWithPost: API returned success=false")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "sendMessageWithPost: Error parsing response", e)
                    }
                },
                { error ->
                    Log.e(TAG, "sendMessageWithPost: Volley error", error)
                    saveToSyncQueue("messages_send.php", payload)
                    Toast.makeText(this, "Post will be shared when online", Toast.LENGTH_SHORT).show()
                }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    return hashMapOf(
                        "messageId" to messageId,
                        "senderId" to myUid,
                        "receiverId" to recipientUid,
                        "messageType" to "post",
                        "content" to "",
                        "postId" to post.postId,
                        "imageUrl" to (post.imageUrl.ifEmpty { post.imageBase64 })
                    )
                }
            }
            queue.add(stringRequest)
        } else {
            Log.w(TAG, "sendMessageWithPost: No network, saving to sync queue")
            saveToSyncQueue("messages_send.php", payload)
            Toast.makeText(this, "Post will be shared when online", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Post Comment ---
    private fun postComment() {
        Log.d(TAG, "postComment: Starting")

        val localPostId = postId
        if (localPostId.isNullOrEmpty()) {
            Log.e(TAG, "postComment: postId is null or empty")
            Toast.makeText(this, "Error: Post ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val text = etCommentInput.text.toString().trim()
        if (text.isEmpty()) {
            Log.d(TAG, "postComment: Comment text is empty")
            return
        }

        if (currentPost == null) {
            Log.e(TAG, "postComment: currentPost is null")
            return
        }

        btnPostComment.isEnabled = false
        etCommentInput.isEnabled = false

        val commentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val payload = JSONObject().apply {
            put("postId", localPostId)
            put("uid", myUid)
            put("text", text)
            put("commentId", commentId)
            put("username", myUsername)
            put("createdAt", timestamp)
        }

        if (isNetworkAvailable(this)) {
            val url = "${AppGlobals.BASE_URL}posts_comment_add.php"
            Log.d(TAG, "postComment: Posting to API, URL=$url")

            val stringRequest = object : StringRequest(
                Request.Method.POST, url,
                { response ->
                    Log.d(TAG, "postComment: Response: $response")
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            val dataObj = json.getJSONObject("data")
                            saveCommentToDb(
                                dataObj.getString("commentId"),
                                dataObj.getString("postId"),
                                dataObj.getString("uid"),
                                dataObj.getString("username"),
                                dataObj.getString("text"),
                                dataObj.getLong("createdAt")
                            )
                            etCommentInput.text.clear()
                            loadCommentsFromDb()
                            Log.d(TAG, "postComment: Comment posted successfully")
                        } else {
                            Toast.makeText(this, "Failed to post comment: ${json.getString("message")}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "postComment: API returned success=false")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "postComment: Error parsing response", e)
                    } finally {
                        etCommentInput.isEnabled = true
                        btnPostComment.isEnabled = true
                    }
                },
                { error ->
                    Log.e(TAG, "postComment: Volley error", error)
                    saveToSyncQueue("posts_comment_add.php", payload)
                    saveCommentToDb(commentId, localPostId, myUid, myUsername, text, timestamp)
                    etCommentInput.text.clear()
                    loadCommentsFromDb()
                    etCommentInput.isEnabled = true
                    btnPostComment.isEnabled = true
                }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    return hashMapOf(
                        "postId" to localPostId,
                        "uid" to myUid,
                        "text" to text
                    )
                }
            }
            queue.add(stringRequest)
        } else {
            Log.w(TAG, "postComment: No network, saving to sync queue")
            saveToSyncQueue("posts_comment_add.php", payload)
            saveCommentToDb(commentId, localPostId, myUid, myUsername, text, timestamp)
            etCommentInput.text.clear()
            loadCommentsFromDb()
            etCommentInput.isEnabled = true
            btnPostComment.isEnabled = true
        }
    }

    // --- Save comment to DB ---
    private fun saveCommentToDb(id: String, postId: String, uid: String, username: String, text: String, timestamp: Long) {
        Log.d(TAG, "saveCommentToDb: Saving commentId=$id")
        try {
            val cv = ContentValues().apply {
                put(DB.Comment.COLUMN_COMMENT_ID, id)
                put(DB.Comment.COLUMN_POST_ID, postId)
                put(DB.Comment.COLUMN_UID, uid)
                put(DB.Comment.COLUMN_USERNAME, username)
                put(DB.Comment.COLUMN_TEXT, text)
                put(DB.Comment.COLUMN_CREATED_AT, timestamp)
            }
            dbHelper.writableDatabase.insertWithOnConflict(
                DB.Comment.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE
            )
            Log.d(TAG, "saveCommentToDb: Comment saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "saveCommentToDb: Error saving to DB", e)
        }
    }

    private fun decodeBase64ToBitmap(raw: String?): android.graphics.Bitmap? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "decodeBase64ToBitmap: Error decoding", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity destroyed")
    }

    // --- Helper Functions ---

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d(TAG, "saveToSyncQueue: Endpoint=$endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
                put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
                put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            }
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            runOnUiThread {
                Toast.makeText(this, "Offline. Action will sync later.", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "saveToSyncQueue: Saved to queue successfully")
        } catch (e: Exception) {
            Log.e(TAG, "saveToSyncQueue: Failed to save", e)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
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

    // --- Inner Adapter for Comments ---
    class CommentAdapter(private val comments: List<Comment>, private val myUid: String) :
        RecyclerView.Adapter<CommentAdapter.CommentVH>() {

        companion object {
            private const val TAG = "CommentAdapter"
        }

        inner class CommentVH(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: ImageView = v.findViewById(R.id.commentAvatar)
            val text: TextView = v.findViewById(R.id.commentText)
            val timestamp: TextView = v.findViewById(R.id.commentTimestamp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
            return CommentVH(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(h: CommentVH, position: Int) {
            try {
                val comment = comments[position]
                h.text.text = "${comment.username}  ${comment.text}"

                val timeAgo = DateUtils.getRelativeTimeSpanString(
                    comment.createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
                h.timestamp.text = timeAgo

                // Load avatar
                try {
                    h.avatar.loadUserAvatar(comment.uid, myUid, R.drawable.oval)
                } catch (e: Exception) {
                    Log.e(TAG, "onBindViewHolder: Error loading avatar", e)
                    h.avatar.setImageResource(R.drawable.oval)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onBindViewHolder: Error at position $position", e)
            }
        }

        override fun getItemCount() = comments.size
    }
}