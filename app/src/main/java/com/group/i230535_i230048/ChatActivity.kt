package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var myUid: String = ""
    private var myUsername: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var adapter: MessageAdapter

    private val messagesList = mutableListOf<Message>()
    private var chatId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""

    private val PICK_IMAGE = 102

    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    // For polling new messages
    private val messagePollingHandler = Handler(Looper.getMainLooper())
    private var messagePollingRunnable: Runnable? = null
    private var lastMessageTimestamp: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        myUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        myUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"

        if (myUid.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        otherUserId = intent.getStringExtra("userId") ?: ""
        otherUserName = intent.getStringExtra("username") ?: ""

        if (otherUserId.isEmpty()) {
            Toast.makeText(this, "Invalid user data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        chatId = if (myUid < otherUserId) {
            "${myUid}_${otherUserId}"
        } else {
            "${otherUserId}_${myUid}"
        }

        Log.d("ChatActivity", "Chat ID: $chatId")

        registerScreenshotObserver()
        setupViews()
        setupRecyclerView()

        // Ensure chat exists in backend
        ensureChatExists()
    }

    override fun onStart() {
        super.onStart()
        loadMessagesFromDb()
        fetchMessagesFromApi()
        startPollingUserStatus()
        startPollingMessages()
    }

    override fun onStop() {
        super.onStop()
        stopPollingUserStatus()
        stopPollingMessages()
    }

    /**
     * Ensures the chat exists on the server before fetching messages
     * Uses chat_create.php
     */
    private fun ensureChatExists() {
        if (!isNetworkAvailable(this)) return

        val url = AppGlobals.BASE_URL + "chat_create.php"
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val cleaned = cleanJsonResponse(response)
                    val json = JSONObject(cleaned)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val createdNow = dataObj.optBoolean("createdNow", false)
                        Log.d("ChatActivity", "Chat exists/created. createdNow: $createdNow")
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error ensuring chat exists: ${e.message}")
                }
            },
            { error -> Log.e("ChatActivity", "Error creating chat: ${error.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid1"] = myUid
                params["uid2"] = otherUserId
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun registerScreenshotObserver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 1)
            // Handled by activity result launcher in a real app, skipping strict check for snippet
        }

        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("Screenshot", "Screenshot detected!")
                sendScreenshotEventToApi()
            }
        }

        try {
            contentResolver.registerContentObserver(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
            )
        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to register screenshot observer: ${e.message}")
        }
    }

    private fun setupViews() {
        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.username).text = otherUserName

        // Avatar is a TextView showing initials, not an ImageView
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

        // --- CALL BUTTONS ---
        findViewById<ImageView>(R.id.voice).setOnClickListener {
            // Initiate audio call
            initiateCall(isVideoCall = false)
        }

        findViewById<ImageView>(R.id.video).setOnClickListener {
            // Initiate video call
            initiateCall(isVideoCall = true)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.messagesRecyclerView)
        adapter = MessageAdapter(messagesList, myUid) { message ->
            showMessageOptions(message)
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    /**
     * Helper to clean PHP errors from JSON response
     */
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Uncaught")) {
            Log.e("ChatActivity", "PHP Fatal error in response")
            return "{\"success\": false, \"message\": \"Server error\"}"
        }

        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            val jsonStart = cleaned.indexOf("{\"")
            if (jsonStart > 0) {
                cleaned = cleaned.substring(jsonStart)
            } else if (jsonStart == -1) {
                return "{\"success\": false, \"message\": \"Invalid response\"}"
            }
        }

        return cleaned
    }

    private fun loadMessagesFromDb() {
        Log.d("ChatActivity", "Loading messages from DB...")
        messagesList.clear()
        val db = dbHelper.readableDatabase
        val selection = "(${DB.Message.COLUMN_SENDER_ID} = ? AND ${DB.Message.COLUMN_RECEIVER_ID} = ?) OR " +
                "(${DB.Message.COLUMN_SENDER_ID} = ? AND ${DB.Message.COLUMN_RECEIVER_ID} = ?)"
        val selectionArgs = arrayOf(myUid, otherUserId, otherUserId, myUid)

        val cursor = db.query(
            DB.Message.TABLE_NAME, null, selection, selectionArgs,
            null, null, DB.Message.COLUMN_TIMESTAMP + " ASC"
        )

        while (cursor.moveToNext()) {
            try {
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_TIMESTAMP))
                messagesList.add(
                    Message(
                        messageId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_MESSAGE_ID)) ?: "",
                        senderId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_SENDER_ID)) ?: "",
                        receiverId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_RECEIVER_ID)) ?: "",
                        messageType = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_MESSAGE_TYPE)) ?: "text",
                        content = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_CONTENT)) ?: "",
                        imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IMAGE_URL)) ?: "",
                        postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_POST_ID)) ?: "",
                        timestamp = timestamp,
                        isEdited = cursor.getInt(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IS_EDITED)) == 1,
                        isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IS_DELETED)) == 1
                    )
                )
                // Track latest timestamp
                if (timestamp > lastMessageTimestamp) {
                    lastMessageTimestamp = timestamp
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error parsing message from DB: ${e.message}")
            }
        }
        cursor.close()

        Log.d("ChatActivity", "Loaded ${messagesList.size} messages from DB")
        adapter.notifyDataSetChanged()
        if (messagesList.isNotEmpty()) {
            recyclerView.scrollToPosition(messagesList.size - 1)
        }
    }

    /**
     * Fetch all messages using messages_get.php (existing API)
     */
    private fun fetchMessagesFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("ChatActivity", "Offline, skipping API message fetch.")
            return
        }

        // Use existing API: messages_get.php
        val url = AppGlobals.BASE_URL + "messages_get.php?chatId=$chatId&uid=$myUid&limit=100"
        Log.d("ChatActivity", "Fetching messages from: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleaned = cleanJsonResponse(response)
                    val json = JSONObject(cleaned)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val messagesArray = dataObj.getJSONArray("messages")

                        val newMessages = mutableListOf<Message>()
                        for (i in 0 until messagesArray.length()) {
                            val msgObj = messagesArray.getJSONObject(i)

                            // Parse timestamp - handle both old and new field names
                            val createdAt = msgObj.optLong("createdAt",
                                msgObj.optLong("timestamp", System.currentTimeMillis()))

                            newMessages.add(
                                Message(
                                    messageId = msgObj.optString("messageId", msgObj.optString("message_id", "")),
                                    senderId = msgObj.optString("senderId", msgObj.optString("sender_uid", "")),
                                    receiverId = msgObj.optString("receiverId", msgObj.optString("receiver_uid", "")),
                                    messageType = msgObj.optString("messageType", msgObj.optString("type", "text")),
                                    content = msgObj.optString("content", msgObj.optString("text", "")),
                                    imageUrl = msgObj.optString("mediaBase64", msgObj.optString("image_url", "")),
                                    postId = msgObj.optString("postId", msgObj.optString("post_id", "")),
                                    timestamp = createdAt,
                                    isEdited = msgObj.optBoolean("edited", msgObj.optInt("is_edited", 0) == 1),
                                    isDeleted = msgObj.optBoolean("deleted", msgObj.optInt("is_deleted", 0) == 1),
                                    editableUntil = msgObj.optLong("editableUntil", createdAt + 300000)
                                )
                            )

                            // Track latest timestamp
                            if (createdAt > lastMessageTimestamp) {
                                lastMessageTimestamp = createdAt
                            }
                        }

                        // Save to DB
                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            val selection = "(${DB.Message.COLUMN_SENDER_ID} = ? AND ${DB.Message.COLUMN_RECEIVER_ID} = ?) OR " +
                                    "(${DB.Message.COLUMN_SENDER_ID} = ? AND ${DB.Message.COLUMN_RECEIVER_ID} = ?)"
                            val selectionArgs = arrayOf(myUid, otherUserId, otherUserId, myUid)
                            db.delete(DB.Message.TABLE_NAME, selection, selectionArgs)

                            for (msg in newMessages) {
                                val cv = ContentValues()
                                cv.put(DB.Message.COLUMN_MESSAGE_ID, msg.messageId)
                                cv.put(DB.Message.COLUMN_SENDER_ID, msg.senderId)
                                cv.put(DB.Message.COLUMN_RECEIVER_ID, msg.receiverId)
                                cv.put(DB.Message.COLUMN_MESSAGE_TYPE, msg.messageType)
                                cv.put(DB.Message.COLUMN_CONTENT, msg.content)
                                cv.put(DB.Message.COLUMN_IMAGE_URL, msg.imageUrl)
                                cv.put(DB.Message.COLUMN_POST_ID, msg.postId)
                                cv.put(DB.Message.COLUMN_TIMESTAMP, msg.timestamp)
                                cv.put(DB.Message.COLUMN_IS_EDITED, if (msg.isEdited) 1 else 0)
                                cv.put(DB.Message.COLUMN_IS_DELETED, if (msg.isDeleted) 1 else 0)
                                db.insert(DB.Message.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }

                        loadMessagesFromDb()
                        Log.d("ChatActivity", "✅ Loaded ${newMessages.size} messages from API")
                    } else {
                        Log.w("ChatActivity", "API returned success=false: ${json.optString("message")}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error parsing messages: ${e.message}")
                    e.printStackTrace()
                }
            },
            { error -> Log.e("ChatActivity", "Volley error fetching messages: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    /**
     * Poll for new messages using chat_update.php
     */
    private fun startPollingMessages() {
        // Only enable if you have a specific polling endpoint, for now we disable it
        // to avoid unnecessary calls if not fully implemented.
        // If you want to enable it, uncomment the below code.
        /*
        messagePollingRunnable = object : Runnable {
            override fun run() {
                pollForNewMessages()
                messagePollingHandler.postDelayed(this, 5000) // Poll every 5 seconds
            }
        }
        messagePollingHandler.postDelayed(messagePollingRunnable!!, 5000)
        */
    }

    private fun stopPollingMessages() {
        Log.d("ChatActivity", "Stopping message polling.")
        messagePollingRunnable?.let { messagePollingHandler.removeCallbacks(it) }
    }

    private fun pollForNewMessages() {
        if (!isNetworkAvailable(this)) return

        // NEW API: chat_update.php with since parameter
        val url = AppGlobals.BASE_URL +
                "chat_update.php?chatId=$chatId&uid=$myUid&since=$lastMessageTimestamp"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleaned = cleanJsonResponse(response)
                    val json = JSONObject(cleaned)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val messagesArray = dataObj.getJSONArray("messages")

                        if (messagesArray.length() > 0) {
                            Log.d("ChatActivity", "📨 Received ${messagesArray.length()} new messages")

                            for (i in 0 until messagesArray.length()) {
                                val msgObj = messagesArray.getJSONObject(i)
                                val createdAt = msgObj.getLong("createdAt")

                                val newMsg = Message(
                                    messageId = msgObj.getString("messageId"),
                                    senderId = msgObj.getString("senderId"),
                                    receiverId = msgObj.getString("receiverId"),
                                    messageType = msgObj.optString("messageType", "text"),
                                    content = msgObj.optString("content", ""),
                                    imageUrl = msgObj.optString("mediaBase64", ""),
                                    postId = msgObj.optString("postId", ""),
                                    timestamp = createdAt,
                                    isEdited = msgObj.optBoolean("edited", false),
                                    isDeleted = msgObj.optBoolean("deleted", false),
                                    editableUntil = msgObj.optLong("editableUntil", createdAt + 300000)
                                )

                                // Add to list if not already there
                                if (messagesList.none { it.messageId == newMsg.messageId }) {
                                    messagesList.add(newMsg)
                                    saveMessageToDb(newMsg)
                                }

                                // Update timestamp
                                if (createdAt > lastMessageTimestamp) {
                                    lastMessageTimestamp = createdAt
                                }
                            }

                            // Sort by timestamp and update UI
                            messagesList.sortBy { it.timestamp }
                            adapter.notifyDataSetChanged()
                            recyclerView.scrollToPosition(messagesList.size - 1)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error polling messages: ${e.message}")
                }
            },
            { error -> Log.w("ChatActivity", "Poll error: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun startPollingUserStatus() {
        Log.d("ChatActivity", "Starting status polling...")
        statusRunnable = object : Runnable {
            override fun run() {
                fetchUserStatus()
                statusHandler.postDelayed(this, 15000)
            }
        }
        statusHandler.post(statusRunnable!!)
    }

    private fun stopPollingUserStatus() {
        Log.d("ChatActivity", "Stopping status polling.")
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
    }

    private fun fetchUserStatus() {
        if (!isNetworkAvailable(this)) return

        val url = AppGlobals.BASE_URL + "user_basic_get.php?uid=$otherUserId"
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val cleaned = cleanJsonResponse(response)
                    val json = JSONObject(cleaned)
                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val isOnline = dataObj.optBoolean("isOnline", false)
                        Log.d("ChatActivity", "Other user online: $isOnline")
                        // TODO: Update UI with online indicator
                    }
                } catch (e: Exception) { Log.e("ChatActivity", "Error parsing status: ${e.message}")}
            },
            { error -> Log.e("ChatActivity", "Volley error fetching status: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        Log.d("ChatActivity", "Sending: $text")
        sendMessage("text", text, "", "")
        messageInput.setText("")
    }

    private fun sendMessage(messageType: String, content: String, imageUrl: String, postId: String) {
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val editableUntil = timestamp + 300000

        val message = Message(
            messageId = messageId,
            senderId = myUid,
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

        // Optimistic UI
        messagesList.add(message)
        adapter.notifyItemInserted(messagesList.size - 1)
        recyclerView.scrollToPosition(messagesList.size - 1)

        // Save to DB
        saveMessageToDb(message)

        // Update last timestamp
        lastMessageTimestamp = timestamp

        // API payload
        val payload = JSONObject()
        payload.put("senderUid", myUid)
        payload.put("receiverUid", otherUserId)
        payload.put("messageType", messageType)
        payload.put("content", content)
        if (imageUrl.isNotEmpty()) payload.put("imageBase64", imageUrl)
        if (postId.isNotEmpty()) payload.put("postId", postId)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "messages_send.php"
            Log.d("ChatActivity", "📤 Sending to: $url")
            Log.d("ChatActivity", "📤 sender=$myUid, receiver=$otherUserId, type=$messageType")

            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    Log.d("ChatActivity", "📥 Raw response (${response.length} chars): ${response.take(500)}")
                    try {
                        val cleaned = cleanJsonResponse(response)
                        val json = JSONObject(cleaned)
                        if (json.getBoolean("success")) {
                            Log.d("ChatActivity", "✅ Message sent successfully")
                            // No toast needed - message already shown in UI
                        } else {
                            val errorMsg = json.optString("message", "Unknown error")
                            Log.e("ChatActivity", "❌ Message send failed: $errorMsg")
                            saveToSyncQueue("messages_send.php", payload)
                            runOnUiThread {
                                Toast.makeText(this@ChatActivity, "Send failed: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "❌ Error parsing send response: ${e.message}")
                        Log.e("ChatActivity", "❌ Raw response was: $response")
                        e.printStackTrace()
                        saveToSyncQueue("messages_send.php", payload)
                        runOnUiThread {
                            Toast.makeText(this@ChatActivity, "Parse error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                { error ->
                    Log.e("ChatActivity", "❌ Network error sending message: ${error.message}")
                    error.networkResponse?.let {
                        Log.e("ChatActivity", "Status code: ${it.statusCode}")
                        Log.e("ChatActivity", "Response: ${String(it.data)}")
                    }
                    saveToSyncQueue("messages_send.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["senderUid"] = myUid
                    params["receiverUid"] = otherUserId
                    params["messageType"] = messageType
                    params["content"] = content
                    if (imageUrl.isNotEmpty()) params["imageBase64"] = imageUrl
                    if (postId.isNotEmpty()) params["postId"] = postId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            Log.d("ChatActivity", "📴 No network, saving to sync queue")
            saveToSyncQueue("messages_send.php", payload)
        }
    }

    private fun saveMessageToDb(msg: Message) {
        try {
            val cv = ContentValues()
            cv.put(DB.Message.COLUMN_MESSAGE_ID, msg.messageId)
            cv.put(DB.Message.COLUMN_SENDER_ID, msg.senderId)
            cv.put(DB.Message.COLUMN_RECEIVER_ID, msg.receiverId)
            cv.put(DB.Message.COLUMN_MESSAGE_TYPE, msg.messageType)
            cv.put(DB.Message.COLUMN_CONTENT, msg.content)
            cv.put(DB.Message.COLUMN_IMAGE_URL, msg.imageUrl)
            cv.put(DB.Message.COLUMN_POST_ID, msg.postId)
            cv.put(DB.Message.COLUMN_TIMESTAMP, msg.timestamp)
            cv.put(DB.Message.COLUMN_IS_EDITED, if (msg.isEdited) 1 else 0)
            cv.put(DB.Message.COLUMN_IS_DELETED, if (msg.isDeleted) 1 else 0)
            dbHelper.writableDatabase.insertWithOnConflict(DB.Message.TABLE_NAME, null, cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error saving message to DB: ${e.message}")
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
                    sendMessage("image", "📷 Photo", base64Image, "")
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
        if (message.senderId != myUid) {
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
                    val index = messagesList.indexOfFirst { it.messageId == message.messageId }
                    if (index != -1) {
                        messagesList[index] = message.copy(content = newContent, isEdited = true)
                        adapter.notifyItemChanged(index)
                    }

                    val cv = ContentValues()
                    cv.put(DB.Message.COLUMN_CONTENT, newContent)
                    cv.put(DB.Message.COLUMN_IS_EDITED, 1)
                    dbHelper.writableDatabase.update(DB.Message.TABLE_NAME, cv,
                        "${DB.Message.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))

                    val payload = JSONObject()
                    payload.put("uid", myUid)
                    payload.put("messageId", message.messageId)
                    payload.put("newText", newContent)

                    if (isNetworkAvailable(this)) {
                        val url = AppGlobals.BASE_URL + "messages_edit.php"
                        val stringRequest = object : StringRequest(Request.Method.POST, url,
                            { response -> Log.d("ChatActivity", "Message edit confirmed") },
                            { error ->
                                Log.e("ChatActivity", "Volley error editing: ${error.message}")
                                saveToSyncQueue("messages_edit.php", payload)
                            }) {
                            override fun getParams(): MutableMap<String, String> {
                                val params = HashMap<String, String>()
                                params["uid"] = myUid
                                params["messageId"] = message.messageId
                                params["newText"] = newContent
                                return params
                            }
                        }
                        queue.add(stringRequest)
                    } else {
                        saveToSyncQueue("messages_edit.php", payload)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(message: Message) {
        val index = messagesList.indexOfFirst { it.messageId == message.messageId }
        if (index != -1) {
            messagesList[index] = message.copy(content = "This message was deleted", isDeleted = true)
            adapter.notifyItemChanged(index)
        }

        val cv = ContentValues()
        cv.put(DB.Message.COLUMN_CONTENT, "This message was deleted")
        cv.put(DB.Message.COLUMN_IS_DELETED, 1)
        dbHelper.writableDatabase.update(DB.Message.TABLE_NAME, cv,
            "${DB.Message.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))

        val payload = JSONObject()
        payload.put("uid", myUid)
        payload.put("messageId", message.messageId)

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "message_delete.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response -> Log.d("ChatActivity", "Message delete confirmed") },
                { error ->
                    Log.e("ChatActivity", "Volley error deleting: ${error.message}")
                    saveToSyncQueue("message_delete.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["uid"] = myUid
                    params["messageId"] = message.messageId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("message_delete.php", payload)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendScreenshotEventToApi() {
        val endpoint = "screenshot_log.php"

        val payload = JSONObject()
        payload.put("chatId", chatId)
        payload.put("takerUid", myUid)
        payload.put("receiverUid", otherUserId)
        payload.put("timestamp", System.currentTimeMillis())

        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + endpoint
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    Log.d("ScreenshotEvent", "📸 Screenshot logged")
                    Toast.makeText(this, "Screenshot detected!", Toast.LENGTH_SHORT).show()
                },
                { error ->
                    Log.e("ScreenshotEvent", "Error: ${error.message}")
                    saveToSyncQueue(endpoint, payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["chatId"] = chatId
                    params["takerUid"] = myUid
                    params["receiverUid"] = otherUserId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue(endpoint, payload)
        }
    }

    private fun showUserInfo() {
        Toast.makeText(this, "Viewing info for $otherUserName", Toast.LENGTH_SHORT).show()
    }

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("ChatActivity", "💾 Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            // Only show toast if explicitly offline
            if (!isNetworkAvailable(this)) {
                runOnUiThread {
                    Toast.makeText(this, "Offline. Will sync when connected.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to save to sync queue: ${e.message}")
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

    /**
     * Initiate a voice or video call to the other user
     */
    private fun initiateCall(isVideoCall: Boolean) {
        val callType = if (isVideoCall) "video" else "voice"
        Log.d("ChatActivity", "📞 Initiating $callType call to $otherUserName")

        Toast.makeText(this, "Calling $otherUserName...", Toast.LENGTH_SHORT).show()

        CallManager.initiateCall(
            context = this,
            currentUserId = myUid,
            currentUserName = myUsername,
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            isVideoCall = isVideoCall
        )
    }
}