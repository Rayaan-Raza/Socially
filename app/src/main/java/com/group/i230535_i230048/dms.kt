package com.group.i230535_i230048

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class dms : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var noChatsMessage: TextView

    private val chatList = mutableListOf<ChatSession>()
    private var currentUserId: String = ""

    // --- NEW ---
    // Variables to check if we are sharing
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

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentUserId = currentUser.uid

        // --- NEW ---
        // Check if we were started in "share mode"
        if (intent.getStringExtra("ACTION_MODE") == "SHARE") {
            isShareMode = true
            findViewById<TextView>(R.id.title)?.text = "Share to..." // Optional: Change title
        }
        // --- END NEW ---

        setupRecyclerView()
        loadChats()

        findViewById<ImageView>(R.id.back)?.setOnClickListener { finish() }
    }

    // --- MODIFIED ---
    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.chatsRecyclerView)
        noChatsMessage = findViewById(R.id.no_chats_message)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Pass the new click handler to the adapter
        chatAdapter = ChatAdapter(chatList, currentUserId) { clickedChat ->
            // This 'clickedChat' is the ChatSession object
            if (isShareMode) {
                // If sharing, send the result back
                handleShareClick(clickedChat)
            } else {
                // If not sharing, open the chat
                handleNormalClick(clickedChat)
            }
        }
        recyclerView.adapter = chatAdapter
    }

    // --- NEW ---
    // This is the new logic for "share mode"
    private fun handleShareClick(chat: ChatSession) {
        val otherUserId = chat.participants.keys.firstOrNull { it != currentUserId }
        if (otherUserId == null) {
            Toast.makeText(this, "Error selecting chat.", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a result intent and put the recipient's ID in it
        val resultIntent = Intent()
        resultIntent.putExtra("SELECTED_USER_ID", otherUserId)
        setResult(Activity.RESULT_OK, resultIntent)

        // Close this activity and return to GotoPostActivity
        finish()
    }

    // --- NEW ---
    // This is the original logic for opening a chat
    private fun handleNormalClick(chat: ChatSession) {
        val otherUserId = chat.participants.keys.firstOrNull { it != currentUserId }
        if (otherUserId == null) {
            Toast.makeText(this, "Error opening chat.", Toast.LENGTH_SHORT).show()
            return
        }

        // We need the other user's name to pass to ChatActivity
        database.getReference("users").child(otherUserId).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.getValue(String::class.java) ?: "User"
                    val intent = Intent(this@dms, ChatActivity::class.java)
                    intent.putExtra("userId", otherUserId)
                    intent.putExtra("username", username)
                    startActivity(intent)
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@dms, "Could not load user data.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // --- MODIFIED ---
    // Removed currentUserId param since it's a class variable now
    private fun loadChats() {
        val chatsRef = database.getReference("chats")

        chatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatList.clear()
                android.util.Log.d("DMS_DEBUG", "=== Starting to load chats ===")

                var chatsAdded = 0
                for (chatSnapshot in snapshot.children) {
                    try {
                        val chatId = chatSnapshot.key ?: ""
                        val lastMessage = chatSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
                        val lastMessageTimestamp = chatSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                        val lastMessageSenderId = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java) ?: ""

                        val participantsSnapshot = chatSnapshot.child("participants")
                        val participant0 = participantsSnapshot.child("0").getValue(String::class.java)?.trim() ?: ""
                        val participant1 = participantsSnapshot.child("1").getValue(String::class.java)?.trim() ?: ""

                        if (participant0.isNotEmpty() && participant1.isNotEmpty() &&
                            (participant0 == currentUserId || participant1 == currentUserId)) {

                            val participantsMap = hashMapOf(participant0 to true, participant1 to true)
                            val chat = ChatSession(
                                chatId = chatId,
                                participants = participantsMap,
                                lastMessage = lastMessage,
                                lastMessageTimestamp = lastMessageTimestamp,
                                lastMessageSenderId = lastMessageSenderId
                            )
                            chatList.add(chat)
                            chatsAdded++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DMS_ERROR", "Error processing chat: ${e.message}", e)
                    }
                }

                android.util.Log.d("DMS_DEBUG", "Chats added for this user: $chatsAdded")
                chatList.sortByDescending { it.lastMessageTimestamp }
                chatAdapter.notifyDataSetChanged()

                if (chatList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    noChatsMessage.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    noChatsMessage.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@dms, "Failed to load chats: ${error.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("DMS_ERROR", "Database error: ${error.message}")
            }
        })
    }
}