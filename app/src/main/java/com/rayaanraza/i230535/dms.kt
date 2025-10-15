package com.rayaanraza.i230535

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
            Toast.makeText(this, "You must be logged in to view messages.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }

        setupRecyclerView(currentUser.uid)
        loadChats(currentUser.uid)

        findViewById<ImageView>(R.id.back)?.setOnClickListener { finish() }
    }

    private fun setupRecyclerView(currentUserId: String) {
        recyclerView = findViewById(R.id.chatsRecyclerView)
        noChatsMessage = findViewById(R.id.no_chats_message)
        recyclerView.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(chatList, currentUserId)
        recyclerView.adapter = chatAdapter
    }

    private fun loadChats(currentUserId: String) {
        val chatsRef = database.getReference("chats")

        chatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chatList.clear()

                android.util.Log.d("DMS_DEBUG", "=== Starting to load chats ===")
                android.util.Log.d("DMS_DEBUG", "Current User ID: '$currentUserId'")
                android.util.Log.d("DMS_DEBUG", "Current User ID length: ${currentUserId.length}")

                var totalChatsFound = 0
                var chatsAdded = 0

                // Loop through ALL chats in Firebase
                for (chatSnapshot in snapshot.children) {
                    totalChatsFound++

                    try {
                        // Read the chat ID (this is the key name)
                        val chatId = chatSnapshot.key ?: chatSnapshot.child("chatId").getValue(String::class.java) ?: ""

                        val lastMessage = chatSnapshot.child("lastMessage").getValue(String::class.java) ?: ""
                        val lastMessageTimestamp = chatSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                        val lastMessageSenderId = chatSnapshot.child("lastMessageSenderId").getValue(String::class.java) ?: ""

                        // Read participants array (0 and 1 indices)
                        val participantsSnapshot = chatSnapshot.child("participants")
                        val participant0 = participantsSnapshot.child("0").getValue(String::class.java)?.trim() ?: ""
                        val participant1 = participantsSnapshot.child("1").getValue(String::class.java)?.trim() ?: ""

                        android.util.Log.d("DMS_DEBUG", "--- Chat #$totalChatsFound ---")
                        android.util.Log.d("DMS_DEBUG", "Chat ID: '$chatId'")
                        android.util.Log.d("DMS_DEBUG", "Participant[0]: '$participant0' (length: ${participant0.length})")
                        android.util.Log.d("DMS_DEBUG", "Participant[1]: '$participant1' (length: ${participant1.length})")
                        android.util.Log.d("DMS_DEBUG", "Match with [0]: ${participant0 == currentUserId}")
                        android.util.Log.d("DMS_DEBUG", "Match with [1]: ${participant1 == currentUserId}")

                        // Check if both participants exist and current user is one of them
                        if (participant0.isNotEmpty() && participant1.isNotEmpty() &&
                            (participant0 == currentUserId || participant1 == currentUserId)) {

                            // Create participants map
                            val participantsMap = hashMapOf(
                                participant0 to true,
                                participant1 to true
                            )

                            // Create ChatSession object
                            val chat = ChatSession(
                                chatId = chatId,
                                participants = participantsMap,
                                lastMessage = lastMessage,
                                lastMessageTimestamp = lastMessageTimestamp,
                                lastMessageSenderId = lastMessageSenderId
                            )

                            chatList.add(chat)
                            chatsAdded++

                            val otherUserId = if (participant0 == currentUserId) participant1 else participant0
                            android.util.Log.d("DMS_DEBUG", "✅ ADDED chat with other user: $otherUserId")
                        } else {
                            android.util.Log.d("DMS_DEBUG", "❌ SKIPPED - Not your chat or missing participants")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DMS_ERROR", "Error processing chat: ${e.message}", e)
                    }
                }

                android.util.Log.d("DMS_DEBUG", "=== Summary ===")
                android.util.Log.d("DMS_DEBUG", "Total chats in Firebase: $totalChatsFound")
                android.util.Log.d("DMS_DEBUG", "Chats added for this user: $chatsAdded")

                // Sort by newest first
                chatList.sortByDescending { it.lastMessageTimestamp }

                // Update UI
                chatAdapter.notifyDataSetChanged()

                // DEBUG: Check RecyclerView state
                android.util.Log.d("DMS_DEBUG", "RecyclerView childCount: ${recyclerView.childCount}")
                android.util.Log.d("DMS_DEBUG", "RecyclerView width: ${recyclerView.width}, height: ${recyclerView.height}")
                android.util.Log.d("DMS_DEBUG", "RecyclerView visibility: ${recyclerView.visibility} (0=VISIBLE, 4=INVISIBLE, 8=GONE)")
                android.util.Log.d("DMS_DEBUG", "Adapter item count: ${chatAdapter.itemCount}")
                android.util.Log.d("DMS_DEBUG", "ChatList size: ${chatList.size}")

                if (chatList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    noChatsMessage.visibility = View.VISIBLE
                    android.util.Log.d("DMS_DEBUG", "❌ No chats to display")
                } else {
                    recyclerView.visibility = View.VISIBLE
                    noChatsMessage.visibility = View.GONE
                    Toast.makeText(this@dms, "Loaded ${chatList.size} chat(s)", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("DMS_DEBUG", "✅ Displaying ${chatList.size} chat(s)")

                    // Force RecyclerView to measure and layout
                    recyclerView.post {
                        android.util.Log.d("DMS_DEBUG", "After post - RecyclerView childCount: ${recyclerView.childCount}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@dms, "Failed to load chats: ${error.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("DMS_ERROR", "Database error: ${error.message}")
            }
        })
    }
}