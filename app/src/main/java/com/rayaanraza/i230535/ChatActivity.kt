package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var messagesRef: DatabaseReference
    private lateinit var chatsRef: DatabaseReference

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var adapter: MessageAdapter

    private val messagesList = mutableListOf<Message>()
    private var chatId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""
    private var currentUserId: String = ""

    private val PICK_IMAGE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Firebase setup
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Intent extras
        otherUserId = intent.getStringExtra("userId") ?: ""
        otherUserName = intent.getStringExtra("username") ?: ""

        if (otherUserId.isEmpty()) {
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Chat ID - use underscore like your working chat.kt
        chatId = if (currentUserId < otherUserId) {
            "${currentUserId}_${otherUserId}"
        } else {
            "${otherUserId}_${currentUserId}"
        }

        android.util.Log.d("ChatActivity", "=== Chat Started ===")
        android.util.Log.d("ChatActivity", "Chat ID: $chatId")
        android.util.Log.d("ChatActivity", "Current User: $currentUserId")
        android.util.Log.d("ChatActivity", "Other User: $otherUserId ($otherUserName)")

        // Firebase references - FIXED PATHS
        messagesRef = database.getReference("messages").child(chatId)
        chatsRef = database.getReference("chats").child(chatId)

        android.util.Log.d("ChatActivity", "Messages path: messages/$chatId")
        android.util.Log.d("ChatActivity", "Chats path: chats/$chatId")

        setupViews()
        setupRecyclerView()
        createOrGetChat()
        loadMessages()
        loadOtherUserOnlineStatus()
    }

    private fun setupViews() {
        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.username).text = otherUserName

        val initials = if (otherUserName.isNotEmpty()) {
            otherUserName.split(" ").take(2).map { it.first() }.joinToString("").uppercase()
        } else "?"
        findViewById<TextView>(R.id.avatar).text = initials

        messageInput = findViewById(R.id.message_input)
        findViewById<ImageView>(R.id.sendButton).setOnClickListener { sendTextMessage() }

        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTextMessage()
                true
            } else false
        }

        findViewById<ImageView>(R.id.cameraIcon).setOnClickListener { openGallery() }
        findViewById<ImageView>(R.id.gallery).setOnClickListener { openGallery() }
        findViewById<ImageView>(R.id.extraIcon).setOnClickListener {
            Toast.makeText(this, "Voice messages coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageView>(R.id.stickersIcon).setOnClickListener {
            sendMessage("text", "👋", "", "")
        }
        findViewById<ImageView>(R.id.info).setOnClickListener {
            showUserInfo()
        }

        // Call buttons
        findViewById<ImageView>(R.id.voice).setOnClickListener {
            CallManager.initiateCall(
                context = this,
                currentUserId = currentUserId,
                currentUserName = auth.currentUser?.displayName ?: "You",
                otherUserId = otherUserId,
                otherUserName = otherUserName,
                isVideoCall = false,
                onSuccess = {
                    Toast.makeText(this, "Calling...", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Toast.makeText(this, "Call failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }

        findViewById<ImageView>(R.id.video).setOnClickListener {
            CallManager.initiateCall(
                context = this,
                currentUserId = currentUserId,
                currentUserName = auth.currentUser?.displayName ?: "You",
                otherUserId = otherUserId,
                otherUserName = otherUserName,
                isVideoCall = true,
                onSuccess = {
                    Toast.makeText(this, "Video calling...", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Toast.makeText(this, "Call failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.messagesRecyclerView)
        adapter = MessageAdapter(messagesList, currentUserId) { message ->
            showMessageOptions(message)
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        android.util.Log.d("ChatActivity", "RecyclerView setup complete")
    }

    private fun createOrGetChat() {
        chatsRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                android.util.Log.d("ChatActivity", "Creating new chat...")
                val chatData = hashMapOf(
                    "chatId" to chatId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "lastMessageSenderId" to ""
                )
                chatsRef.setValue(chatData).addOnSuccessListener {
                    android.util.Log.d("ChatActivity", "✅ Chat created")
                }
            } else {
                android.util.Log.d("ChatActivity", "✅ Chat exists")
            }
        }
    }

    private fun loadMessages() {
        android.util.Log.d("ChatActivity", "Loading messages from: messages/$chatId")

        messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    android.util.Log.d("ChatActivity", "=== Messages Loaded ===")
                    android.util.Log.d("ChatActivity", "Snapshot exists: ${snapshot.exists()}")
                    android.util.Log.d("ChatActivity", "Children count: ${snapshot.childrenCount}")

                    messagesList.clear()
                    var count = 0

                    for (child in snapshot.children) {
                        try {
                            val message = child.getValue(Message::class.java)
                            if (message != null) {
                                messagesList.add(message)
                                count++
                                android.util.Log.d("ChatActivity", "Message $count: ${message.content.take(30)}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatActivity", "Error parsing message: ${e.message}")
                        }
                    }

                    android.util.Log.d("ChatActivity", "Total loaded: ${messagesList.size}")

                    adapter.notifyDataSetChanged()
                    if (messagesList.isNotEmpty()) {
                        recyclerView.scrollToPosition(messagesList.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("ChatActivity", "❌ Failed to load: ${error.message}")
                    Toast.makeText(this@ChatActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadOtherUserOnlineStatus() {
        database.getReference("users").child(otherUserId)
            .child("isOnline")
            .addValueEventListener(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.getValue(Boolean::class.java) ?: false
                    android.util.Log.d("ChatActivity", "Other user online: $isOnline")
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) {
            android.util.Log.d("ChatActivity", "Empty message, not sending")
            return
        }

        android.util.Log.d("ChatActivity", "Sending: $text")
        sendMessage("text", text, "", "")
        messageInput.setText("")
    }

    private fun sendMessage(messageType: String, content: String, imageUrl: String, postId: String) {
        val messageRef = messagesRef.push()
        val messageId = messageRef.key ?: return
        val timestamp = System.currentTimeMillis()
        val editableUntil = timestamp + 300000 // 5 minutes

        val message = Message(
            messageId = messageId,
            senderId = currentUserId,
            receiverId = otherUserId,
            messageType = messageType,
            content = content,
            imageUrl = imageUrl,
            postId = postId,
            timestamp = timestamp,
            isEdited = false,
            isDeleted = false,
            editableUntil = editableUntil
        )

        android.util.Log.d("ChatActivity", "Saving to: messages/$chatId/$messageId")

        messageRef.setValue(message)
            .addOnSuccessListener {
                android.util.Log.d("ChatActivity", "✅ Message sent")
                val displayContent = when (messageType) {
                    "image" -> "📷 Photo"
                    "post" -> "📝 Shared a post"
                    else -> content
                }
                updateLastMessage(displayContent, timestamp)
            }
            .addOnFailureListener {
                android.util.Log.e("ChatActivity", "❌ Send failed: ${it.message}")
                Toast.makeText(this, "Failed to send message: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLastMessage(content: String, timestamp: Long) {
        val updates = hashMapOf<String, Any>(
            "lastMessage" to content,
            "lastMessageTimestamp" to timestamp,
            "lastMessageSenderId" to currentUserId
        )
        chatsRef.updateChildren(updates).addOnSuccessListener {
            android.util.Log.d("ChatActivity", "✅ Last message updated")
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                        val source = ImageDecoder.createSource(contentResolver, imageUri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    }
                    val base64Image = encodeImage(bitmap)
                    sendMessage("image", "Sent an image", base64Image, "")
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun showMessageOptions(message: Message) {
        if (message.senderId != currentUserId) {
            Toast.makeText(this, "You can only edit your own messages", Toast.LENGTH_SHORT).show()
            return
        }

        if (!message.canEditOrDelete()) {
            Toast.makeText(this, "Can only edit/delete within 5 minutes", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("Edit", "Delete", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> editMessage(message)
                    1 -> deleteMessage(message)
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun editMessage(message: Message) {
        if (message.messageType != "text") {
            Toast.makeText(this, "Can only edit text messages", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.setText(message.content)
        input.setPadding(50, 30, 50, 30)

        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newContent = input.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    val updates = hashMapOf<String, Any>(
                        "content" to newContent,
                        "isEdited" to true
                    )
                    messagesRef.child(message.messageId).updateChildren(updates)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(message: Message) {
        val updates = hashMapOf<String, Any>(
            "content" to "This message was deleted",
            "isDeleted" to true
        )
        messagesRef.child(message.messageId).updateChildren(updates)
    }

    private fun showUserInfo() {
        Toast.makeText(this, "Viewing info for $otherUserName", Toast.LENGTH_SHORT).show()
    }
}