package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.UUID

class GotoPostActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val myUid = auth.currentUser?.uid ?: ""

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

    private var likeListener: ValueEventListener? = null
    private var commentsListener: ValueEventListener? = null
    private var postListener: ValueEventListener? = null
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
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0) // No bottom padding
            // Adjust input bar for keyboard
            findViewById<View>(R.id.comment_input_bar).setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

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

        loadPostData()
        attachRealtimeListeners()
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

    private fun loadPostData() {
        postListener = db.child("posts").child(postUserId!!).child(postId!!)
            .addValueEventListener(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    val post = snapshot.getValue(Post::class.java)
                    if (post == null) {
                        Toast.makeText(this@GotoPostActivity, "This post may have been deleted.", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                    currentPost = post // Save for sharing

                    // --- Populate Views ---
                    tvUsername.text = post.username
                    tvCaption.text = "${post.username}  ${post.caption}"
                    imgAvatar.loadUserAvatar(post.uid, myUid, R.drawable.oval)

                    if (post.imageUrl.isNotEmpty()) {
                        Glide.with(this@GotoPostActivity).load(post.imageUrl).placeholder(R.drawable.person1).into(imgPost)
                    } else if (post.imageBase64.isNotEmpty()) {
                        decodeBase64ToBitmap(post.imageBase64)?.let { imgPost.setImageBitmap(it) }
                    } else {
                        imgPost.setImageResource(R.drawable.person1)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun attachRealtimeListeners() {
        // (Your existing listeners are fine)
        // 1. Like Listener
        val likeRef = db.child("postLikes").child(postId!!)
        likeListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(s: DataSnapshot) {
                val count = s.childrenCount.toInt()
                val iLike = s.child(myUid).getValue(Boolean::class.java) == true
                tvLikes.text = if (count == 1) "1 like" else "$count likes"
                btnLike.setImageResource(if (iLike) R.drawable.liked else R.drawable.like)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        likeRef.addValueEventListener(likeListener!!)

        // 2. Comments Listener
        val commentsRef = db.child("postComments").child(postId!!).orderByChild("createdAt")
        commentsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentList.clear()
                for (child in snapshot.children) {
                    child.getValue(Comment::class.java)?.let { commentList.add(it) }
                }
                commentAdapter.notifyDataSetChanged()
                // Scroll to bottom if we're at the bottom
                rvComments.post {
                    rvComments.smoothScrollToPosition(commentAdapter.itemCount - 1)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        commentsRef.addValueEventListener(commentsListener!!)
    }

    private fun toggleLike() {
        if (currentPost == null) return
        val likeRef = db.child("postLikes").child(postId!!).child(myUid)

        // Run transaction to get current state
        likeRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val wantLike = snapshot.getValue(Boolean::class.java) != true

                // Update like status
                if (wantLike) likeRef.setValue(true) else likeRef.removeValue()

                // Update like count on post
                val likeCountRef = db.child("posts").child(postUserId!!).child(postId!!).child("likeCount")
                likeCountRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(cur: MutableData): Transaction.Result {
                        val c = (cur.getValue(Long::class.java) ?: 0L)
                        cur.value = (c + if (wantLike) 1L else -1L).coerceAtLeast(0L)
                        return Transaction.success(cur)
                    }
                    override fun onComplete(e: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {}
                })
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- MODIFIED ---
    private fun sharePost() {
        if (currentPost == null) {
            Toast.makeText(this, "Cannot share post, data not loaded.", Toast.LENGTH_SHORT).show()
            return
        }

        // Launch dms.kt INSTEAD of SelectFriendActivity
        val intent = Intent(this, dms::class.java)

        // Add extras to tell dms.kt it's in "share mode"
        intent.putExtra("ACTION_MODE", "SHARE")
        // No need to pass post data, we already have it in this activity.
        // We just need the selected user ID back.

        // Use the launcher
        selectFriendLauncher.launch(intent)
    }

    // --- THIS IS THE FIXED FUNCTION FROM OUR PREVIOUS CONVERSATION ---
    private fun sendMessageWithPost(recipientId: String, post: Post) {
        // Use the correct Chat ID format (with underscore)
        val chatId = if (myUid < recipientId) {
            "${myUid}_${recipientId}"
        } else {
            "${recipientId}_${myUid}"
        }

        // Write to the "messages" node
        val messageRef = db.child("messages").child(chatId).push()
        val messageId = messageRef.key ?: return // Safe check
        val timestamp = System.currentTimeMillis()

        // --- THIS IS THE FIX ---
        // We must use 'this.postId!!' (the class variable) instead of 'post.postId'
        // 'this.postId' is guaranteed to be non-null from the check in onCreate.
        val message = Message(
            messageId = messageId,
            senderId = myUid,
            receiverId = recipientId,
            messageType = "post",
            content = "Shared a post",
            imageUrl = post.imageUrl.ifEmpty { post.imageBase64 },
            postId = this.postId!!,  // <-- THE FIX
            timestamp = timestamp,
            isEdited = false,
            isDeleted = false,
            editableUntil = 0
        )
        // --- END OF FIX ---

        messageRef.setValue(message).addOnSuccessListener {
            Toast.makeText(this, "Post sent!", Toast.LENGTH_SHORT).show()

            // Also update the "lastMessage" in the "chats" node
            updateLastMessage(chatId, "📝 Shared a post", timestamp)

        }.addOnFailureListener { e ->
            // If it still fails, this will tell you why
            android.util.Log.e("GotoPostActivity", "Failed to send message: ${e.message}")
            Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- HELPER FUNCTION FOR sendMessageWithPost ---
    private fun updateLastMessage(chatId: String, content: String, timestamp: Long) {
        val chatsRef = db.child("chats").child(chatId)

        // This ensures the chat exists in the chat list
        val chatData = mapOf(
            "lastMessage" to content,
            "lastMessageTimestamp" to timestamp,
            "lastMessageSenderId" to myUid
        )
        chatsRef.updateChildren(chatData)
    }


    private fun postComment() {
        val text = etCommentInput.text.toString().trim()
        if (text.isEmpty()) {
            return
        }
        if (currentPost == null) {
            Toast.makeText(this, "Cannot comment, post not loaded.", Toast.LENGTH_SHORT).show()
            return
        }

        btnPostComment.isEnabled = false
        etCommentInput.isEnabled = false

        db.child("users").child(myUid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val uname = (s.getValue(String::class.java) ?: "user").ifBlank { "user" }
                    val commentId = UUID.randomUUID().toString()
                    val comment = Comment(
                        commentId = commentId,
                        postId = postId!!,
                        uid = myUid,
                        username = uname,
                        text = text,
                        createdAt = System.currentTimeMillis()
                    )

                    db.child("postComments").child(postId!!).child(commentId).setValue(comment)
                        .addOnSuccessListener {
                            // Clear input
                            etCommentInput.text.clear()
                            etCommentInput.isEnabled = true
                            btnPostComment.isEnabled = true
                            // Update comment count
                            updateCommentCount()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this@GotoPostActivity, "Failed to post comment.", Toast.LENGTH_SHORT).show()
                            etCommentInput.isEnabled = true
                            btnPostComment.isEnabled = true
                        }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@GotoPostActivity, "Failed to get user data.", Toast.LENGTH_SHORT).show()
                    etCommentInput.isEnabled = true
                    btnPostComment.isEnabled = true
                }
            })
    }

    private fun updateCommentCount() {
        val ref = db.child("posts").child(postUserId!!).child(postId!!).child("commentCount")
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(cur: MutableData): Transaction.Result {
                cur.value = ((cur.getValue(Long::class.java) ?: 0L) + 1L)
                return Transaction.success(cur)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
        })
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
        // Detach listeners to prevent memory leaks
        postListener?.let { db.child("posts").child(postUserId!!).child(postId!!).removeEventListener(it) }
        likeListener?.let { db.child("postLikes").child(postId!!).removeEventListener(it) }
        commentsListener?.let { db.child("postComments").child(postId!!).removeEventListener(it) }
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

            // Set text with username
            h.text.text = "${comment.username}  ${comment.text}"

            // Set relative timestamp
            val timeAgo = DateUtils.getRelativeTimeSpanString(
                comment.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            h.timestamp.text = timeAgo

            // Load avatar
            h.avatar.loadUserAvatar(comment.uid, myUid, R.drawable.oval)
        }

        override fun getItemCount() = comments.size
    }
}