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
        enableEdgeToEdge()
        setContentView(R.layout.activity_goto_post)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            findViewById<View>(R.id.comment_input_bar).setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        // --- Setup DB, Volley, and Session ---
        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        myUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        myUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"
        // ---

        postId = intent.getStringExtra("POST_ID")
        postUserId = intent.getStringExtra("USER_ID")

        if (myUid.isEmpty() || postId.isNullOrEmpty() || postUserId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Could not load post.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()
        setupClickListeners()

        // --- New loading flow ---
        loadPostDataFromDb() // Load from local DB first
        fetchPostDetailsFromApi() // Then refresh from network
    }

    private fun initializeViews() {
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
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(commentList, myUid)
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { finish() }
        btnLike.setOnClickListener { toggleLike() }
        btnShare.setOnClickListener { sharePost() }
        btnPostComment.setOnClickListener { postComment() }
    }

    // --- Load data from local SQLite DB ---
    private fun loadPostDataFromDb() {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.Post.TABLE_NAME, null,
            "${DB.Post.COLUMN_POST_ID} = ?", arrayOf(postId),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            val post = Post(
                postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)),
                caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)),
                imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)),
                imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
            )
            currentPost = post // Save for sharing
            populatePostViews(post) // Update UI
        }
        cursor.close()

        // Also load comments from DB
        loadCommentsFromDb()
    }

    // --- Populate UI with Post object ---
    @SuppressLint("SetTextI18n")
    private fun populatePostViews(post: Post) {
        tvUsername.text = post.username
        tvCaption.text = "${post.username}  ${post.caption}"

        // This now uses the migrated loadUserAvatar function
        imgAvatar.loadUserAvatar(post.uid, myUid, R.drawable.oval)

        if (post.imageUrl.isNotEmpty()) {
            Glide.with(this@GotoPostActivity).load(post.imageUrl).placeholder(R.drawable.person1).into(imgPost)
        } else if (post.imageBase64.isNotEmpty()) {
            decodeBase64ToBitmap(post.imageBase64)?.let { imgPost.setImageBitmap(it) }
        } else {
            imgPost.setImageResource(R.drawable.person1)
        }
    }

    // --- Fetch fresh data from API ---
    private fun fetchPostDetailsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("GotoPost", "Offline, skipping API refresh.")
            return
        }

        // 1. Fetch Post using post_get.php endpoint
        val postUrl = AppGlobals.BASE_URL + "post_get.php?postId=$postId"
        val postRequest = StringRequest(Request.Method.GET, postUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val postObj = json.getJSONObject("data")
                        val cv = ContentValues()
                        cv.put(DB.Post.COLUMN_POST_ID, postObj.getString("postId"))
                        cv.put(DB.Post.COLUMN_UID, postObj.getString("uid"))
                        cv.put(DB.Post.COLUMN_USERNAME, postObj.optString("username", "user"))
                        cv.put(DB.Post.COLUMN_CAPTION, postObj.optString("caption", ""))
                        cv.put(DB.Post.COLUMN_IMAGE_URL, postObj.optString("imageUrl", ""))
                        cv.put(DB.Post.COLUMN_IMAGE_BASE64, postObj.optString("imageBase64", ""))
                        cv.put(DB.Post.COLUMN_CREATED_AT, postObj.getLong("createdAt"))
                        cv.put(DB.Post.COLUMN_LIKE_COUNT, postObj.getLong("likeCount"))
                        cv.put(DB.Post.COLUMN_COMMENT_COUNT, postObj.getLong("commentCount"))
                        dbHelper.writableDatabase.insertWithOnConflict(DB.Post.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        loadPostDataFromDb() // Reload from DB to update UI

                        // Note: The API doesn't return "isLikedByCurrentUser", we'd need a separate endpoint
                        // For now, keep the like state as-is or check locally
                        tvLikes.text = "${postObj.getLong("likeCount")} likes"
                    }
                } catch (e: Exception) { Log.e("GotoPost", "Error parsing post: ${e.message}") }
            },
            { error -> Log.e("GotoPost", "Volley error fetching post: ${error.message}") }
        )
        queue.add(postRequest)

        // 2. Fetch Comments
        fetchCommentsFromApi()
    }

    private fun fetchCommentsFromApi() {
        val commentsUrl = AppGlobals.BASE_URL + "post_comments_get.php?postId=$postId&limit=100"
        val commentsRequest = StringRequest(Request.Method.GET, commentsUrl,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val commentsArray = dataObj.getJSONArray("comments")
                        val db = dbHelper.writableDatabase

                        db.delete(DB.Comment.TABLE_NAME, "${DB.Comment.COLUMN_POST_ID} = ?", arrayOf(postId))

                        db.beginTransaction()
                        try {
                            for (i in 0 until commentsArray.length()) {
                                val commentObj = commentsArray.getJSONObject(i)
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
                        loadCommentsFromDb()
                    }
                } catch (e: Exception) { Log.e("GotoPost", "Error parsing comments: ${e.message}") }
            },
            { error -> Log.e("GotoPost", "Volley error fetching comments: ${error.message}") }
        )
        queue.add(commentsRequest)
    }

    private fun loadCommentsFromDb() {
        val db = dbHelper.readableDatabase
        commentList.clear()
        val cursor = db.query(
            DB.Comment.TABLE_NAME, null,
            "${DB.Comment.COLUMN_POST_ID} = ?", arrayOf(postId),
            null, null, DB.Comment.COLUMN_CREATED_AT + " ASC"
        )
        while (cursor.moveToNext()) {
            commentList.add(
                Comment(
                    commentId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_COMMENT_ID)),
                    postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_POST_ID)),
                    uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_UID)),
                    username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_USERNAME)),
                    text = cursor.getString(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_TEXT)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Comment.COLUMN_CREATED_AT))
                )
            )
        }
        cursor.close()
        commentAdapter.notifyDataSetChanged()
        rvComments.post {
            rvComments.smoothScrollToPosition(commentAdapter.itemCount - 1)
        }
    }

    // --- CORRECTED: Migrated to Volley + Offline Queue ---
    private fun toggleLike() {
        // Create a local, non-nullable variable *before* the StringRequest
        val localPostId = postId
        if (localPostId == null) {
            Toast.makeText(this, "Error: Post ID is missing", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentPost == null) return

        // Optimistic UI update
        val currentLikeRes = btnLike.drawable.constantState
        val wantLike = currentLikeRes != ContextCompat.getDrawable(this, R.drawable.liked)?.constantState
        btnLike.setImageResource(if (wantLike) R.drawable.liked else R.drawable.like)

        val likedValue = if (wantLike) "1" else "0"

        val payload = JSONObject()
        payload.put("postId", localPostId)
        payload.put("uid", myUid)
        payload.put("liked", likedValue)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "posts_like.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            val dataObj = json.getJSONObject("data")
                            val newCount = dataObj.getInt("likeCount")
                            tvLikes.text = "$newCount likes"
                            Log.d("GotoPost", "Like action successful")
                        }
                    } catch (e: Exception) {
                        Log.e("GotoPost", "Error parsing like response: ${e.message}")
                    }
                },
                { error ->
                    Log.e("GotoPost", "Volley error liking: ${error.message}")
                    saveToSyncQueue("posts_like.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["postId"] = localPostId
                    params["uid"] = myUid
                    params["liked"] = likedValue
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("posts_like.php", payload)
        }
    }

    private fun sharePost() {
        if (currentPost == null) {
            Toast.makeText(this, "Cannot share post, data not loaded.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, dms::class.java)
        intent.putExtra("ACTION_MODE", "SHARE")
        selectFriendLauncher.launch(intent)
    }

    // --- CORRECTED: Migrated to Volley + Offline Queue ---
    private fun sendMessageWithPost(recipientId: String, post: Post) {
        // Create a local, non-nullable variable
        val localPostId = postId
        if (localPostId == null) {
            Toast.makeText(this, "Error: Post ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = System.currentTimeMillis()
        val payload = JSONObject()
        payload.put("senderUid", myUid)
        payload.put("receiverUid", recipientId)
        payload.put("messageType", "post")
        payload.put("content", "Shared a post")
        payload.put("postId", localPostId)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "messages_send.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Toast.makeText(this, "Post sent!", Toast.LENGTH_SHORT).show()
                            // TODO: Save sent message to local DB
                        } else {
                            Toast.makeText(this, "Failed to send: ${json.getString("message")}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) { Log.e("GotoPost", "Error parsing send_message: ${e.message}") }
                },
                { error ->
                    Log.e("GotoPost", "Volley error send_message: ${error.message}")
                    saveToSyncQueue("messages_send.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["senderUid"] = myUid
                    params["receiverUid"] = recipientId
                    params["messageType"] = "post"
                    params["content"] = "Shared a post"
                    params["postId"] = localPostId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("messages_send.php", payload)
        }
    }

    // --- CORRECTED: Migrated to Volley + Offline Queue ---
    private fun postComment() {
        // Create a local, non-nullable variable
        val localPostId = postId
        if (localPostId == null) {
            Toast.makeText(this, "Error: Post ID is missing", Toast.LENGTH_SHORT).show()
            return
        }

        val text = etCommentInput.text.toString().trim()
        if (text.isEmpty()) return
        if (currentPost == null) return

        btnPostComment.isEnabled = false
        etCommentInput.isEnabled = false

        val commentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val payload = JSONObject()
        payload.put("postId", localPostId)
        payload.put("uid", myUid)
        payload.put("text", text)
        payload.put("commentId", commentId)
        payload.put("username", myUsername)
        payload.put("createdAt", timestamp)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "posts_comment_add.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
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
                        } else {
                            Toast.makeText(this@GotoPostActivity, "Failed to post comment: ${json.getString("message")}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("GotoPost", "Error parsing add_comment: ${e.message}")
                    } finally {
                        etCommentInput.isEnabled = true
                        btnPostComment.isEnabled = true
                    }
                },
                { error ->
                    Log.e("GotoPost", "Volley error add_comment: ${error.message}")
                    saveToSyncQueue("posts_comment_add.php", payload)
                    // Optimistic update for offline error
                    saveCommentToDb(commentId, localPostId, myUid, myUsername, text, timestamp)
                    etCommentInput.text.clear()
                    loadCommentsFromDb()
                    etCommentInput.isEnabled = true
                    btnPostComment.isEnabled = true
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["postId"] = localPostId
                    params["uid"] = myUid
                    params["text"] = text
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // Offline - Save to queue
            saveToSyncQueue("posts_comment_add.php", payload)

            // Optimistic UI: Add to local DB immediately
            saveCommentToDb(commentId, localPostId, myUid, myUsername, text, timestamp)
            etCommentInput.text.clear()
            loadCommentsFromDb()

            etCommentInput.isEnabled = true
            btnPostComment.isEnabled = true
        }
    }

    // --- NEW: Helper to save comment to DB ---
    private fun saveCommentToDb(id: String, postId: String, uid: String, username: String, text: String, timestamp: Long) {
        try {
            val cv = ContentValues()
            cv.put(DB.Comment.COLUMN_COMMENT_ID, id)
            cv.put(DB.Comment.COLUMN_POST_ID, postId)
            cv.put(DB.Comment.COLUMN_UID, uid)
            cv.put(DB.Comment.COLUMN_USERNAME, username)
            cv.put(DB.Comment.COLUMN_TEXT, text)
            cv.put(DB.Comment.COLUMN_CREATED_AT, timestamp)
            dbHelper.writableDatabase.insertWithOnConflict(DB.Comment.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("GotoPost", "Error saving comment to DB: ${e.message}")
        }
    }

    private fun decodeBase64ToBitmap(raw: String?): android.graphics.Bitmap? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No listeners to detach
    }

    // --- HELPER FUNCTIONS FOR OFFLINE QUEUE & NETWORK ---

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("GotoPost", "Saving to sync queue. Endpoint: $endpoint")
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
            Log.e("GotoPost", "Failed to save to sync queue: ${e.message}")
        }
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

    // --- Inner Adapter for Comments ---
    class CommentAdapter(private val comments: List<Comment>, private val myUid: String) :
        RecyclerView.Adapter<CommentAdapter.CommentVH>() {

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
            val comment = comments[position]
            h.text.text = "${comment.username}  ${comment.text}"
            val timeAgo = DateUtils.getRelativeTimeSpanString(
                comment.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            h.timestamp.text = timeAgo

            // This now uses the migrated loadUserAvatar function
            h.avatar.loadUserAvatar(comment.uid, myUid, R.drawable.oval)
        }

        override fun getItemCount() = comments.size
    }
}