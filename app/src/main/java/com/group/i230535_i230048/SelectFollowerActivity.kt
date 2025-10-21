package com.group.i230535_i230048

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SelectFollowerActivity : AppCompatActivity() {

    private lateinit var rvFollowers: RecyclerView
    private lateinit var adapter: FollowerAdapter
    private val followerList = mutableListOf<User>() // Use your User data class

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val myUid = auth.currentUser?.uid ?: ""

    private var postId: String? = null
    private var postImageUrl: String? = null // Can be URL or Base64

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_select_follower)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get post details from intent
        postId = intent.getStringExtra("POST_ID")
        postImageUrl = intent.getStringExtra("POST_IMAGE_URL") // Expect URL or Base64

        if (myUid.isEmpty() || postId.isNullOrEmpty() || postImageUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Missing post data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }

        setupRecyclerView()
        loadFollowers()
    }

    private fun setupRecyclerView() {
        rvFollowers = findViewById(R.id.rvFollowers)
        adapter = FollowerAdapter(followerList) { selectedUser ->
            sendPostToFollower(selectedUser)
        }
        rvFollowers.adapter = adapter
    }

    private fun loadFollowers() {
        val followersRef = db.child("followers").child(myUid)

        followersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@SelectFollowerActivity, "You have no followers.", Toast.LENGTH_SHORT).show()
                    return
                }

                val followerIds = mutableListOf<String>()
                for (child in snapshot.children) {
                    // Assuming the key is the follower's UID and value is true
                    if (child.getValue(Boolean::class.java) == true) {
                        child.key?.let { followerIds.add(it) }
                    }
                }

                if (followerIds.isEmpty()) {
                    Toast.makeText(this@SelectFollowerActivity, "You have no followers.", Toast.LENGTH_SHORT).show()
                    return
                }

                fetchFollowerDetails(followerIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SelectFollowerActivity, "Failed to load followers.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchFollowerDetails(followerIds: List<String>) {
        followerList.clear()
        var loadedCount = 0

        for (followerId in followerIds) {
            db.child("users").child(followerId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java) // Use your User data class
                    if (user != null) {
                        // Ensure UID is set if not part of the data class from Firebase
                        user.uid = snapshot.key ?: ""
                        followerList.add(user)
                    }
                    loadedCount++
                    if (loadedCount == followerIds.size) {
                        adapter.notifyDataSetChanged() // Update adapter when all details are fetched
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    loadedCount++
                    if (loadedCount == followerIds.size) {
                        adapter.notifyDataSetChanged()
                    }
                }
            })
        }
    }

    private fun sendPostToFollower(recipient: User) {
        val recipientId = recipient.uid
        // Determine a consistent chat room ID
        val chatRoomId = if (myUid > recipientId) "$myUid-$recipientId" else "$recipientId-$myUid"

        // Reference to the specific chat under the 'chats' node
        val chatMessagesRef = db.child("chats").child(chatRoomId)

        // Generate a unique message ID
        val messagePushRef = chatMessagesRef.push()
        val messageId = messagePushRef.key ?: return // Exit if key generation fails

        // Create the message object
        val message = Message(
            messageId = messageId,
            senderId = myUid,
            timestamp = System.currentTimeMillis(),
            messageType = "post", // Special type for shared posts
            imageUrl = postImageUrl ?: "", // Image URL or Base64 from the post
            postId = postId ?: "" // Include post ID if needed later
            // You might not need 'content' for a post message, or set it to "Shared a post"
            // receiverId is implicitly known via the chatRoomId
        )

        // Set the message value in the database
        messagePushRef.setValue(message).addOnSuccessListener {
            Toast.makeText(this, "Post sent!", Toast.LENGTH_SHORT).show()

            // After sending, navigate to the ChatActivity
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", recipientId)
                putExtra("username", recipient.username)
                // Optionally pass chatId if ChatActivity uses it directly
                // putExtra("chatId", chatRoomId)
            }
            startActivity(intent)
            finish() // Close this activity

        }.addOnFailureListener {
            Toast.makeText(this, "Failed to send post.", Toast.LENGTH_SHORT).show()
        }

        // Also update the last message info for the chat list (optional but good)
        updateChatMetadata(chatRoomId, "Sent a post", message.timestamp)
    }

    // Optional: Update chat metadata for the chat list (dms activity)
    private fun updateChatMetadata(chatRoomId: String, lastMsg: String, timestamp: Long) {
        val chatMetaRef = db.child("chats_metadata").child(chatRoomId) // Or wherever you store chat list info
        val updates = mapOf(
            "lastMessage" to lastMsg,
            "lastMessageTimestamp" to timestamp,
            "lastMessageSenderId" to myUid
        )
        chatMetaRef.updateChildren(updates)
    }
}

// Make sure you have a User data class, e.g.:
// data class User(
//    var uid: String = "",
//    var username: String = "",
//    var profilePictureUrl: String? = null,
//    // Add other fields as needed
// )