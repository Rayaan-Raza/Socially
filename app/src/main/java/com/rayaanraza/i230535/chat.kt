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

class chat : AppCompatActivity() {

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

        // Initialize Firebase components
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        currentUserId = auth.currentUser?.uid ?: ""

        // Get data from the intent that started this activity
        otherUserId = intent.getStringExtra("userId") ?: ""
        otherUserName = intent.getStringExtra("username") ?: ""
        chatId = generateChatId(currentUserId, otherUserId)

        // Set up database references
        messagesRef = database.getReference("messages").child(chatId)
        chatsRef = database.getReference("chats").child(chatId)

        // Set up UI and listeners
        setupViews()
        setupRecyclerView()
        createOrGetChat()
        loadMessages()
        loadOtherUserOnlineStatus()
    }

    private fun setupViews() {
        // Setup header UI
        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.username).text = otherUserName

        // Set avatar initials
        val initials = if (otherUserName.isNotEmpty()) {
            otherUserName.split(" ").take(2).map { it.first() }.joinToString("").uppercase()
        } else { "?" }
        findViewById<TextView>(R.id.avatar).text = initials

        messageInput = findViewById(R.id.message_input)

        // Setup send button click listener
        findViewById<ImageView>(R.id.sendButton).setOnClickListener { sendTextMessage() }

        // Setup listener for the keyboard's "Enter" or "Send" action
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTextMessage()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // Setup other icon listeners
        findViewById<ImageView>(R.id.cameraIcon).setOnClickListener { openGallery() }
        findViewById<ImageView>(R.id.gallery).setOnClickListener { openGallery() }
        findViewById<ImageView>(R.id.extraIcon).setOnClickListener {
            Toast.makeText(this, "Voice messages coming soon!", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageView>(R.id.stickersIcon).setOnClickListener {
            sendMessage("text", "👋", "", "")
        }

        // Info button
        findViewById<ImageView>(R.id.info).setOnClickListener {
            showUserInfo()
        }

        // Create a unique channel name for the Agora call
        val channelName = generateChatId(currentUserId, otherUserId)

        // Voice Call Button Listener
        findViewById<ImageView>(R.id.voice).setOnClickListener {
            val callIntent = Intent(this, call_page::class.java).apply {
                putExtra("CHANNEL_NAME", channelName)
                putExtra("USER_NAME", otherUserName)
                putExtra("IS_VIDEO_CALL", false)
            }
            startActivity(callIntent)
        }

        // Video Call Button Listener
        findViewById<ImageView>(R.id.video).setOnClickListener {
            val callIntent = Intent(this, call_page::class.java).apply {
                putExtra("CHANNEL_NAME", channelName)
                putExtra("USER_NAME", otherUserName)
                putExtra("IS_VIDEO_CALL", true)
            }
            startActivity(callIntent)
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
    }

    private fun generateChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    private fun createOrGetChat() {
        chatsRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val chatData = hashMapOf(
                    "chatId" to chatId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis(),
                    "lastMessageSenderId" to ""
                )
                chatsRef.setValue(chatData)
            }
        }
    }

    private fun loadMessages() {
        messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messagesList.clear()
                    for (child in snapshot.children) {
                        val message = child.getValue(Message::class.java)
                        if (message != null) {
                            messagesList.add(message)
                        }
                    }
                    adapter.notifyDataSetChanged()
                    if (messagesList.isNotEmpty()) {
                        recyclerView.scrollToPosition(messagesList.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@chat, "Failed to load messages", Toast.LENGTH_SHORT).show()
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
                    // Update UI if you have an online status indicator
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        sendMessage(
            messageType = "text",
            content = text,
            imageUrl = "",
            postId = ""
        )

        messageInput.setText("")
    }

    private fun sendMessage(messageType: String, content: String, imageUrl: String, postId: String) {
        val messageId = messagesRef.push().key ?: return
        val timestamp = System.currentTimeMillis()
        val editableUntil = timestamp + 300000 // 5 minutes

        val message = Message(
            messageId, currentUserId, otherUserId, messageType, content,
            imageUrl, postId, timestamp, isEdited = false, isDeleted = false, editableUntil
        )

        messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                val displayContent = when (messageType) {
                    "image" -> "📷 Photo"
                    "post" -> "📝 Shared a post"
                    else -> content
                }
                updateLastMessage(displayContent, timestamp)

                // Send push notification to the other user
                //sendPushNotification(content, messageType)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send message: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

//    private fun sendPushNotification(messageContent: String, messageType: String) {
//        // Get current user's name from Firebase
//        database.getReference("users").child(currentUserId)
//            .child("username")
//            .get()
//            .addOnSuccessListener { snapshot ->
//                val senderName = snapshot.getValue(String::class.java) ?: "Someone"
//
//                // Format message for notification
//                val notificationBody = when (messageType) {
//                    "image" -> "📷 Sent a photo"
//                    "post" -> "📝 Shared a post"
//                    else -> messageContent
//                }
//
//                // Send notification
//                NotificationHelper.sendMessageNotification(
//                    receiverId = otherUserId,
//                    senderName = senderName,
//                    messageContent = notificationBody
//                )
//            }
//    }

    private fun updateLastMessage(content: String, timestamp: Long) {
        val updates = hashMapOf<String, Any>(
            "lastMessage" to content,
            "lastMessageTimestamp" to timestamp,
            "lastMessageSenderId" to currentUserId
        )
        chatsRef.updateChildren(updates)
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
                    sendMessage(
                        messageType = "image",
                        content = "Sent an image",
                        imageUrl = base64Image,
                        postId = ""
                    )

                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
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