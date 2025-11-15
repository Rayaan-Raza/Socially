package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues // CHANGED
import android.content.Context // CHANGED
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager // CHANGED
import android.net.NetworkCapabilities // CHANGED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler // CHANGED
import android.os.Looper // CHANGED
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
// REMOVED: All Firebase imports
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import com.group.i230535_i230048.AppDbHelper // CHANGED
import com.group.i230535_i230048.DB // CHANGED
import org.json.JSONObject // CHANGED
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    // --- CHANGED: Removed Firebase, added Volley, DB, and session ---
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var myUid: String = ""
    private var myUsername: String = ""
    // ---

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var adapter: MessageAdapter

    private val messagesList = mutableListOf<Message>()
    private var chatId: String = ""
    private var otherUserId: String = ""
    private var otherUserName: String = ""

    private val PICK_IMAGE = 102

    // --- CHANGED: Added for online status polling ---
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null
    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- CHANGED: Setup DB, Volley, and Session ---
        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        myUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        myUsername = prefs.getString(AppGlobals.KEY_USERNAME, "user") ?: "user"
        // ---

        if (myUid.isEmpty()) {
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

        chatId = if (myUid < otherUserId) {
            "${myUid}_${otherUserId}"
        } else {
            "${otherUserId}_${myUid}"
        }

        Log.d("ChatActivity", "Chat ID: $chatId")

        // REMOVED: Firebase references

        // This is a requirement (Task #12)
        registerScreenshotObserver()

        setupViews()
        setupRecyclerView()

        // REMOVED: createOrGetChat (backend's send_message.php will handle this)
    }

    // --- CHANGED: Moved message loading to onStart ---
    override fun onStart() {
        super.onStart()
        // 1. Load messages from local DB first
        loadMessagesFromDb()
        // 2. Fetch new messages from API
        fetchMessagesFromApi()
        // 3. Start polling for user's online status
        startPollingUserStatus()
    }

    override fun onStop() {
        super.onStop()
        // 4. Stop polling when activity is not visible
        stopPollingUserStatus()
    }

    private fun registerScreenshotObserver() {
        // --- This logic for screenshot detection is fine ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 1)
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("Screenshot", "Screenshot detected!")
                // CHANGED: Call our new API function
                sendScreenshotEventToApi()
            }
        }

        contentResolver.registerContentObserver(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun setupViews() {
        findViewById<ImageView>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.username).text = otherUserName

        // TODO: Load avatar using loadUserAvatar
        // findViewById<ImageView>(R.id.avatar_image).loadUserAvatar(otherUserId, myUid, R.drawable.oval)

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

        // --- CHANGED: Call buttons need to be migrated (Task #7) ---
        findViewById<ImageView>(R.id.voice).setOnClickListener {
            // REMOVED: CallManager.initiateCall
            // TODO: This now needs to call your backend to get an Agora token
            // and send an FCM to the other user.
            Toast.makeText(this, "Calling... (feature pending)", Toast.LENGTH_SHORT).show()
            // initiateAgoraCall(isVideoCall = false)
        }

        findViewById<ImageView>(R.id.video).setOnClickListener {
            // REMOVED: CallManager.initiateCall
            Toast.makeText(this, "Video calling... (feature pending)", Toast.LENGTH_SHORT).show()
            // initiateAgoraCall(isVideoCall = true)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.messagesRecyclerView)
        adapter = MessageAdapter(messagesList, myUid) { message ->
            showMessageOptions(message)
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true // Keep this
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    // REMOVED: createOrGetChat()

    // --- NEW: Load messages from local SQLite DB ---
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
            messagesList.add(
                Message(
                    messageId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_MESSAGE_ID)),
                    senderId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_SENDER_ID)),
                    receiverId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_RECEIVER_ID)),
                    messageType = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_MESSAGE_TYPE)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_CONTENT)),
                    imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IMAGE_URL)),
                    postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_POST_ID)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_TIMESTAMP)),
                    isEdited = cursor.getInt(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IS_EDITED)) == 1,
                    isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(DB.Message.COLUMN_IS_DELETED)) == 1
                )
            )
        }
        cursor.close()

        Log.d("ChatActivity", "Loaded ${messagesList.size} messages from DB")
        adapter.notifyDataSetChanged()
        if (messagesList.isNotEmpty()) {
            recyclerView.scrollToPosition(messagesList.size - 1)
        }
    }

    // --- NEW: Fetch new messages from API ---
    private fun fetchMessagesFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("ChatActivity", "Offline, skipping API message fetch.")
            return
        }

        val url = AppGlobals.BASE_URL + "get_messages.php?chat_id=$chatId" // (from ApiService.kt)
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val listType = object : TypeToken<List<Message>>() {}.type
                        val newMessages: List<Message> = Gson().fromJson(dataArray.toString(), listType)

                        // Save new messages to DB
                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            // Simple approach: delete old, insert new
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

                        // Reload from DB to refresh UI
                        loadMessagesFromDb()
                    }
                } catch (e: Exception) { Log.e("ChatActivity", "Error parsing messages: ${e.message}") }
            },
            { error -> Log.e("ChatActivity", "Volley error fetching messages: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    // --- NEW: Polling for online status (Task #11) ---
    private fun startPollingUserStatus() {
        Log.d("ChatActivity", "Starting status polling...")
        statusRunnable = object : Runnable {
            override fun run() {
                fetchUserStatus()
                statusHandler.postDelayed(this, 15000) // Poll every 15 seconds
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

        // TODO: Dev A needs to create this API
        val url = AppGlobals.BASE_URL + "get_user_status.php?uid=$otherUserId"
        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val isOnline = json.optBoolean("isOnline", false)
                        Log.d("ChatActivity", "Other user online: $isOnline")
                        // TODO: Update your UI (e.g., a green dot)
                        // val onlineIndicator = findViewById<ImageView>(R.id.online_indicator)
                        // onlineIndicator.visibility = if (isOnline) View.VISIBLE else View.GONE
                    }
                } catch (e: Exception) { Log.e("ChatActivity", "Error parsing status: ${e.message}")}
            },
            { error -> Log.e("ChatActivity", "Volley error fetching status: ${error.message}") }
        )
        queue.add(stringRequest)
    }
    // --- End of status polling ---

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return

        Log.d("ChatActivity", "Sending: $text")
        sendMessage("text", text, "", "")
        messageInput.setText("")
    }

    // --- CHANGED: Migrated sendMessage to Volley + Offline Queue ---
    private fun sendMessage(messageType: String, content: String, imageUrl: String, postId: String) {
        val messageId = UUID.randomUUID().toString() // Create local ID
        val timestamp = System.currentTimeMillis()
        val editableUntil = timestamp + 300000 // 5 minutes

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

        // 1. Optimistic UI update
        messagesList.add(message)
        adapter.notifyItemInserted(messagesList.size - 1)
        recyclerView.scrollToPosition(messagesList.size - 1)

        // 2. Save to local DB immediately
        saveMessageToDb(message, "PENDING") // TODO: Add a "status" field to DB.Message

        // 3. Create payload for API
        val payload = JSONObject()
        payload.put("messageId", messageId)
        payload.put("sender_id", myUid)
        payload.put("receiver_id", otherUserId)
        payload.put("message_type", messageType)
        payload.put("content", content)
        payload.put("imageUrl", imageUrl) // This will be Base64 for images
        payload.put("post_id", postId)
        payload.put("timestamp", timestamp)

        // 4. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "send_message.php"
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("ChatActivity", "✅ Message sent and confirmed by server")
                            // TODO: Update message status in DB to "SENT"
                        } else {
                            Log.e("ChatActivity", "API error sending message: ${json.getString("message")}")
                            saveToSyncQueue("send_message.php", payload)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatActivity", "Error parsing send response: ${e.message}")
                        saveToSyncQueue("send_message.php", payload)
                    }
                },
                { error ->
                    Log.e("ChatActivity", "Volley error sending message: ${error.message}")
                    saveToSyncQueue("send_message.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    // This assumes send_message.php takes form-urlencoded
                    // If it takes multipart, this needs to be a different request type
                    val params = HashMap<String, String>()
                    params["messageId"] = messageId
                    params["sender_id"] = myUid
                    params["receiver_id"] = otherUserId
                    params["message_type"] = messageType
                    params["content"] = content
                    params["imageUrl"] = imageUrl
                    params["post_id"] = postId
                    params["timestamp"] = timestamp.toString()
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // Offline
            saveToSyncQueue("send_message.php", payload)
        }
    }

    // --- NEW: Helper to save message to DB ---
    private fun saveMessageToDb(msg: Message, status: String) { // TODO: Add 'status' param
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
            // cv.put(DB.Message.COLUMN_STATUS, status) // Add this column to your DB
            dbHelper.writableDatabase.insertWithOnConflict(DB.Message.TABLE_NAME, null, cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.e("ChatActivity", "Error saving message to DB: ${e.message}")
        }
    }

    // REMOVED: updateLastMessage (Backend should handle this)

    private fun openGallery() {
        // (No changes here)
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
                    // CHANGED: This now calls our new offline-ready function
                    sendMessage("image", "📷 Photo", base64Image, "")
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        // (No changes here)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun showMessageOptions(message: Message) {
        // (No changes here, this logic is fine)
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

    // --- CHANGED: Migrated editMessage to Volley + Offline Queue ---
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
                    // 1. Optimistic UI
                    val index = messagesList.indexOfFirst { it.messageId == message.messageId }
                    if (index != -1) {
                        messagesList[index] = message.copy(content = newContent, isEdited = true)
                        adapter.notifyItemChanged(index)
                    }

                    // 2. Update local DB
                    val cv = ContentValues()
                    cv.put(DB.Message.COLUMN_CONTENT, newContent)
                    cv.put(DB.Message.COLUMN_IS_EDITED, 1)
                    dbHelper.writableDatabase.update(DB.Message.TABLE_NAME, cv,
                        "${DB.Message.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))

                    // 3. Create payload
                    val payload = JSONObject()
                    payload.put("message_id", message.messageId)
                    payload.put("new_content", newContent)

                    // 4. Check network and send or queue
                    if (isNetworkAvailable(this)) {
                        val url = AppGlobals.BASE_URL + "edit_message.php" // (from ApiService.kt)
                        val stringRequest = object : StringRequest(Request.Method.POST, url,
                            { response -> Log.d("ChatActivity", "Message edit confirmed by server") },
                            { error ->
                                Log.e("ChatActivity", "Volley error editing: ${error.message}")
                                saveToSyncQueue("edit_message.php", payload)
                            }) {
                            override fun getParams(): MutableMap<String, String> {
                                val params = HashMap<String, String>()
                                params["message_id"] = message.messageId
                                params["new_content"] = newContent
                                return params
                            }
                        }
                        queue.add(stringRequest)
                    } else {
                        saveToSyncQueue("edit_message.php", payload)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- CHANGED: Migrated deleteMessage to Volley + Offline Queue ---
    private fun deleteMessage(message: Message) {
        // 1. Optimistic UI
        val index = messagesList.indexOfFirst { it.messageId == message.messageId }
        if (index != -1) {
            messagesList[index] = message.copy(content = "This message was deleted", isDeleted = true)
            adapter.notifyItemChanged(index)
        }

        // 2. Update local DB
        val cv = ContentValues()
        cv.put(DB.Message.COLUMN_CONTENT, "This message was deleted")
        cv.put(DB.Message.COLUMN_IS_DELETED, 1)
        dbHelper.writableDatabase.update(DB.Message.TABLE_NAME, cv,
            "${DB.Message.COLUMN_MESSAGE_ID} = ?", arrayOf(message.messageId))

        // 3. Create payload
        val payload = JSONObject()
        payload.put("message_id", message.messageId)

        // 4. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "delete_message.php" // (from ApiService.kt)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response -> Log.d("ChatActivity", "Message delete confirmed by server") },
                { error ->
                    Log.e("ChatActivity", "Volley error deleting: ${error.message}")
                    saveToSyncQueue("delete_message.php", payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["message_id"] = message.messageId
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            saveToSyncQueue("delete_message.php", payload)
        }
    }

    // --- CHANGED: Migrated to Volley + Offline Queue (Task #12) ---
    @SuppressLint("MissingPermission")
    private fun sendScreenshotEventToApi() {
        // TODO: Dev A needs to create this API endpoint
        val endpoint = "report_screenshot.php"

        // 1. Create Payload
        val payload = JSONObject()
        payload.put("chatId", chatId)
        payload.put("takerId", myUid)
        payload.put("receiverId", otherUserId)
        payload.put("timestamp", System.currentTimeMillis())

        // 2. Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + endpoint
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    Log.d("ScreenshotEvent", "📸 Screenshot logged to server for chat: $chatId")
                    Toast.makeText(this, "Screenshot detected!", Toast.LENGTH_SHORT).show()
                },
                { error ->
                    Log.e("ScreenshotEvent", "❌ Failed to log screenshot: ${error.message}")
                    saveToSyncQueue(endpoint, payload)
                }) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["chatId"] = chatId
                    params["takerId"] = myUid
                    params["receiverId"] = otherUserId
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

    // --- NEW: HELPER FUNCTIONS FOR OFFLINE QUEUE & NETWORK ---

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("ChatActivity", "Saving to sync queue. Endpoint: $endpoint")
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
}