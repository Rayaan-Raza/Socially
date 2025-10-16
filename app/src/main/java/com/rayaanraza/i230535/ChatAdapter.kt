package com.rayaanraza.i230535

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val chatList: List<ChatSession>,
    private val currentUserId: String
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.chat_avatar)
        val userName: TextView = itemView.findViewById(R.id.chat_user_name)
        val lastMessage: TextView = itemView.findViewById(R.id.chat_last_message)
        val timestamp: TextView = itemView.findViewById(R.id.chat_timestamp)

        fun bind(chat: ChatSession) {
            try {
                android.util.Log.d("ChatAdapter", "Starting bind for chat: ${chat.chatId}")

                // Get the other user's ID using the helper method
                val otherUserId = chat.getOtherUserId(currentUserId)
                android.util.Log.d("ChatAdapter", "Other user ID: $otherUserId")

                if (otherUserId.isEmpty()) {
                    // Fallback: something went wrong with participants
                    userName.text = "Unknown User"
                    profileImage.setImageResource(R.drawable.circular_background)
                    lastMessage.text = "Error loading chat"
                    android.util.Log.e("ChatAdapter", "Empty other user ID")
                    return
                }

                // Load other user's details from Firebase
                loadUserDetails(otherUserId)

                // Display last message
                if (chat.lastMessage.isNotEmpty()) {
                    val messagePrefix = if (chat.lastMessageSenderId == currentUserId) "You: " else ""
                    lastMessage.text = messagePrefix + chat.lastMessage
                } else {
                    lastMessage.text = "No messages yet"
                }

                // Format and display timestamp
                timestamp.text = formatTimestamp(chat.lastMessageTimestamp)

                android.util.Log.d("ChatAdapter", "Bind completed for chat: ${chat.chatId}")
            } catch (e: Exception) {
                android.util.Log.e("ChatAdapter", "Error in bind: ${e.message}", e)
            }
        }

        private fun loadUserDetails(userId: String) {
            val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

            android.util.Log.d("ChatAdapter", "Loading user details for: $userId")

            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val username = snapshot.child("username").getValue(String::class.java) ?: "Unknown User"

                    // Try multiple possible field names for profile picture
                    val profileImageUrl = snapshot.child("profilePictureUrl").getValue(String::class.java)
                        ?: snapshot.child("profileImage").getValue(String::class.java)
                        ?: snapshot.child("profileImageUrl").getValue(String::class.java)

                    userName.text = username

                    android.util.Log.d("ChatAdapter", "User: $username, Profile URL: $profileImageUrl")

                    // Load profile image using Glide
                    if (!profileImageUrl.isNullOrEmpty()) {
                        try {
                            Glide.with(itemView.context)
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.circular_background)
                                .error(R.drawable.circular_background)
                                .into(profileImage)
                            android.util.Log.d("ChatAdapter", "Glide loading image for $username")
                        } catch (e: Exception) {
                            android.util.Log.e("ChatAdapter", "Error loading image: ${e.message}")
                            profileImage.setImageResource(R.drawable.circular_background)
                        }
                    } else {
                        android.util.Log.d("ChatAdapter", "No profile image URL for $username")
                        profileImage.setImageResource(R.drawable.circular_background)
                    }

                    // IMPORTANT: Set click listener here after we have the username
                    itemView.setOnClickListener {
                        val context = itemView.context
                        val intent = Intent(context, ChatActivity::class.java).apply {
                            putExtra("userId", userId)
                            putExtra("username", username)
                        }
                        context.startActivity(intent)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("ChatAdapter", "Failed to load user: ${error.message}")
                    userName.text = "Unknown User"
                    profileImage.setImageResource(R.drawable.circular_background)
                }
            })
        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp == 0L) return ""

            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "Just now" // Less than 1 minute
                diff < 3_600_000 -> "${diff / 60_000}m" // Less than 1 hour
                diff < 86_400_000 -> "${diff / 3_600_000}h" // Less than 24 hours
                diff < 604_800_000 -> { // Less than 7 days
                    val days = diff / 86_400_000
                    "${days}d"
                }
                else -> {
                    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        try {
            android.util.Log.d("ChatAdapter", "Creating ViewHolder")
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat, parent, false)
            android.util.Log.d("ChatAdapter", "ViewHolder created successfully")
            return ChatViewHolder(view)
        } catch (e: Exception) {
            android.util.Log.e("ChatAdapter", "Error creating ViewHolder: ${e.message}", e)
            throw e
        }
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        try {
            android.util.Log.d("ChatAdapter", "Binding position: $position")
            holder.bind(chatList[position])
            android.util.Log.d("ChatAdapter", "Bound position: $position successfully")
        } catch (e: Exception) {
            android.util.Log.e("ChatAdapter", "Error binding position $position: ${e.message}", e)
        }
    }

    override fun getItemCount(): Int = chatList.size
}