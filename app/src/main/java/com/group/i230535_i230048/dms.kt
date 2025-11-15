package com.group.i230535_i230048

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
// REMOVED: All Firebase imports
import com.google.gson.Gson // CHANGED: Added Gson to parse the API response
import com.google.gson.reflect.TypeToken // CHANGED: Added for Gson list parsing
import com.group.i230535_i230048.AppDbHelper
import com.group.i230535_i230048.DB
import org.json.JSONObject

class dms : AppCompatActivity() {

    // --- CHANGED: Removed Firebase, added Volley, DB, and session ---
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""
    // ---

    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var noChatsMessage: TextView

    private val chatList = mutableListOf<ChatSession>()
    private var isShareMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dms)
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
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // ---

        if (intent.getStringExtra("ACTION_MODE") == "SHARE") {
            isShareMode = true
            findViewById<TextView>(R.id.title)?.text = "Share to..."
        }

        setupRecyclerView()
        findViewById<ImageView>(R.id.back)?.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        // --- CHANGED: Offline-first loading ---
        loadChatsFromDb()
        fetchChatsFromApi()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.chatsRecyclerView)
        noChatsMessage = findViewById(R.id.no_chats_message)
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(chatList, currentUserId) { clickedChat ->
            if (isShareMode) {
                handleShareClick(clickedChat)
            } else {
                handleNormalClick(clickedChat)
            }
        }
        recyclerView.adapter = chatAdapter
    }

    private fun handleShareClick(chat: ChatSession) {
        // CHANGED: Logic is simpler, 'otherUser' is now directly on the chat object
        val otherUser = chat.getOtherUser(currentUserId)
        if (otherUser == null) {
            Toast.makeText(this, "Error selecting chat.", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent()
        resultIntent.putExtra("SELECTED_USER_ID", otherUser.uid)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun handleNormalClick(chat: ChatSession) {
        // CHANGED: Logic is simpler, 'otherUser' is now directly on the chat object
        val otherUser = chat.getOtherUser(currentUserId)
        if (otherUser == null) {
            Toast.makeText(this, "Error opening chat.", Toast.LENGTH_SHORT).show()
            return
        }

        // We already have the username, no need to fetch it.
        val intent = Intent(this@dms, ChatActivity::class.java)
        intent.putExtra("userId", otherUser.uid)
        intent.putExtra("username", otherUser.username)
        startActivity(intent)
    }

    // --- NEW: Load chat list from local SQLite DB ---
    private fun loadChatsFromDb() {
        Log.d("DMS", "Loading chats from local DB...")
        chatList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.ChatSessionInfo.TABLE_NAME, null, null, null, null, null,
            DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP + " DESC"
        )

        while (cursor.moveToNext()) {
            // Re-construct the ChatSession object from our denormalized table
            val otherUserId = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_OTHER_USER_ID))
            val otherUser = User(
                uid = otherUserId,
                username = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_OTHER_USERNAME)),
                profilePictureUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_OTHER_PIC_URL))
            )

            chatList.add(
                ChatSession(
                    chatId = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_CHAT_ID)),
                    participants = listOf(currentUserId, otherUserId),
                    participantDetails = listOf(otherUser), // Only need the other user
                    lastMessage = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE)),
                    lastMessageTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP)),
                    lastMessageSenderId = cursor.getString(cursor.getColumnIndexOrThrow(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_SENDER_ID))
                )
            )
        }
        cursor.close()
        chatAdapter.notifyDataSetChanged()

        updateNoChatsMessage()
    }

    // --- NEW: Fetch chat list from API ---
    private fun fetchChatsFromApi() {
        Log.d("DMS", "Fetching chats from API...")
        val url = AppGlobals.BASE_URL + "get_chats.php?user_id=$currentUserId" // (from ApiService.kt)

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        // Use Gson to parse the JSON array into a List<ChatSession>
                        val listType = object : TypeToken<List<ChatSession>>() {}.type
                        val sessions: List<ChatSession> = Gson().fromJson(dataArray.toString(), listType)

                        saveChatsToDb(sessions) // Save to SQLite
                        loadChatsFromDb() // Reload from DB to refresh UI

                    } else {
                        Log.w("DMS", "API error fetching chats: ${json.getString("message")}")
                    }
                } catch (e: Exception) {
                    Log.e("DMS", "Error parsing chat list: ${e.message}")
                }
            },
            { error ->
                Log.e("DMS", "Volley error fetching chats: ${error.message}")
            }
        )
        queue.add(stringRequest)
    }

    // --- NEW: Helper to save API response to DB ---
    private fun saveChatsToDb(sessions: List<ChatSession>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Clear old chat list
            db.delete(DB.ChatSessionInfo.TABLE_NAME, null, null)

            for (session in sessions) {
                val otherUser = session.getOtherUser(currentUserId) ?: continue // Skip if no other user

                val cv = ContentValues()
                cv.put(DB.ChatSessionInfo.COLUMN_CHAT_ID, session.chatId)
                cv.put(DB.ChatSessionInfo.COLUMN_OTHER_USER_ID, otherUser.uid)
                cv.put(DB.ChatSessionInfo.COLUMN_OTHER_USERNAME, otherUser.username)
                cv.put(DB.ChatSessionInfo.COLUMN_OTHER_PIC_URL, otherUser.profilePictureUrl)
                cv.put(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE, session.lastMessage)
                cv.put(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP, session.lastMessageTimestamp)
                cv.put(DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_SENDER_ID, session.lastMessageSenderId)

                db.insert(DB.ChatSessionInfo.TABLE_NAME, null, cv)
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e("DMS", "Error saving chats to DB: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    // --- NEW: Helper to update empty message ---
    private fun updateNoChatsMessage() {
        if (chatList.isEmpty()) {
            recyclerView.visibility = View.GONE
            noChatsMessage.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noChatsMessage.visibility = View.GONE
        }
    }
}