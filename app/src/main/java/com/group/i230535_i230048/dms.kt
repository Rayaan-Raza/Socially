package com.group.i230535_i230048

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import org.json.JSONObject

class dms : AppCompatActivity() {

    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""

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

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (intent.getStringExtra("ACTION_MODE") == "SHARE") {
            isShareMode = true
            findViewById<TextView>(R.id.title)?.text = "Share to..."
        }

        setupRecyclerView()
        findViewById<ImageView>(R.id.back)?.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
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
        val otherUser = chat.getOtherUser(currentUserId)
        if (otherUser == null) {
            Toast.makeText(this, "Error opening chat.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this@dms, ChatActivity::class.java)
        intent.putExtra("userId", otherUser.uid)
        intent.putExtra("username", otherUser.username)
        startActivity(intent)
    }

    private fun loadChatsFromDb() {
        Log.d("DMS", "Loading chats from local DB...")
        chatList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.ChatSessionInfo.TABLE_NAME, null, null, null, null, null,
            DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP + " DESC"
        )

        while (cursor.moveToNext()) {
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
                    participantDetails = listOf(otherUser),
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

    /**
     * Helper to clean PHP errors from JSON response
     */
    private fun cleanJsonResponse(response: String): String {
        var cleaned = response.trim()

        // Check for PHP fatal errors
        if (cleaned.contains("Fatal error") || cleaned.contains("Parse error") || cleaned.contains("Uncaught")) {
            Log.e("DMS", "❌ PHP Fatal error in response")
            Log.e("DMS", "Raw response: ${cleaned.take(500)}")
            return "{\"success\": false, \"message\": \"Server PHP error\"}"
        }

        // If response doesn't start with JSON, try to find it
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            Log.w("DMS", "Response doesn't start with JSON, trying to clean...")
            val jsonStart = cleaned.indexOf("{\"")
            if (jsonStart > 0) {
                val beforeJson = cleaned.substring(0, jsonStart)
                Log.e("DMS", "PHP output before JSON: $beforeJson")
                cleaned = cleaned.substring(jsonStart)
            } else if (jsonStart == -1) {
                Log.e("DMS", "No valid JSON found in response")
                return "{\"success\": false, \"message\": \"Invalid server response\"}"
            }
        }

        return cleaned
    }

    private fun fetchChatsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("DMS", "Offline, skipping API fetch.")
            return
        }

        Log.d("DMS", "📤 Fetching chats from API...")
        val url = AppGlobals.BASE_URL + "chats_list_with_users.php?uid=$currentUserId&limit=50"
        Log.d("DMS", "📤 URL: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d("DMS", "📥 Raw response (${response.length} chars): ${response.take(500)}")
                try {
                    val cleaned = cleanJsonResponse(response)
                    Log.d("DMS", "Cleaned JSON: ${cleaned.take(200)}")
                    val json = JSONObject(cleaned)

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val chatsArray = dataObj.getJSONArray("chats")
                        Log.d("DMS", "✅ Found ${chatsArray.length()} chats")

                        val sessions = mutableListOf<ChatSession>()
                        for (i in 0 until chatsArray.length()) {
                            try {
                                val chatObj = chatsArray.getJSONObject(i)
                                Log.d("DMS", "Parsing chat $i: ${chatObj.toString().take(200)}")

                                // Parse participants - PHP returns object {"uid1": true, "uid2": true}, not array
                                val participants = mutableListOf<String>()
                                if (chatObj.has("participants")) {
                                    val participantsObj = chatObj.get("participants")
                                    if (participantsObj is org.json.JSONArray) {
                                        // It's an array
                                        val participantsArray = participantsObj as org.json.JSONArray
                                        for (j in 0 until participantsArray.length()) {
                                            participants.add(participantsArray.getString(j))
                                        }
                                    } else if (participantsObj is org.json.JSONObject) {
                                        // It's an object/map - keys are the UIDs
                                        val participantsMap = participantsObj as org.json.JSONObject
                                        val keys = participantsMap.keys()
                                        while (keys.hasNext()) {
                                            participants.add(keys.next())
                                        }
                                    }
                                }
                                Log.d("DMS", "Participants: $participants")

                                // Parse otherUser object - PHP uses different field names
                                val otherUser = if (chatObj.has("otherUser") && !chatObj.isNull("otherUser")) {
                                    val otherUserObj = chatObj.getJSONObject("otherUser")
                                    Log.d("DMS", "otherUser object: ${otherUserObj.toString().take(200)}")

                                    // PHP returns: uid, username, fullName, avatar, avatarType, isOnline, lastSeen
                                    // We need to map avatar to profilePictureUrl
                                    val avatarValue = otherUserObj.optString("avatar", "")
                                    val avatarType = otherUserObj.optString("avatarType", "")

                                    User(
                                        uid = otherUserObj.optString("uid", ""),
                                        username = otherUserObj.optString("username", "Unknown"),
                                        fullName = otherUserObj.optString("fullName", ""),
                                        profilePictureUrl = if (avatarType == "url") avatarValue else "",
                                        photo = if (avatarType == "base64") avatarValue else ""
                                    )
                                } else {
                                    Log.w("DMS", "Chat $i missing or null otherUser object!")
                                    User(uid = "", username = "Unknown")
                                }

                                Log.d("DMS", "Other user: ${otherUser.username} (${otherUser.uid})")

                                sessions.add(
                                    ChatSession(
                                        chatId = chatObj.optString("chatId", ""),
                                        participants = participants,
                                        participantDetails = listOf(otherUser),
                                        lastMessage = chatObj.optString("lastMessage", ""),
                                        lastMessageTimestamp = chatObj.optLong("lastMessageTimestamp", 0L),
                                        lastMessageSenderId = chatObj.optString("lastMessageSenderId", "")
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("DMS", "Error parsing chat at index $i: ${e.message}")
                                e.printStackTrace()
                            }
                        }

                        saveChatsToDb(sessions)
                        loadChatsFromDb()
                        Log.d("DMS", "✅ Successfully loaded ${sessions.size} chats")

                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.e("DMS", "❌ API error: $errorMsg")
                        Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("DMS", "❌ Error parsing chat list: ${e.message}")
                    Log.e("DMS", "❌ Raw response was: ${response.take(500)}")
                    e.printStackTrace()
                    Toast.makeText(this, "Error loading chats: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("DMS", "❌ Volley error: ${error.message}")
                error.networkResponse?.let {
                    Log.e("DMS", "Status code: ${it.statusCode}")
                    try {
                        Log.e("DMS", "Response body: ${String(it.data).take(500)}")
                    } catch (e: Exception) { }
                }
                Toast.makeText(this, "Network error loading chats", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(stringRequest)
    }

    private fun saveChatsToDb(sessions: List<ChatSession>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(DB.ChatSessionInfo.TABLE_NAME, null, null)

            for (session in sessions) {
                val otherUser = session.getOtherUser(currentUserId) ?: continue

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

    private fun updateNoChatsMessage() {
        if (chatList.isEmpty()) {
            recyclerView.visibility = View.GONE
            noChatsMessage.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noChatsMessage.visibility = View.GONE
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