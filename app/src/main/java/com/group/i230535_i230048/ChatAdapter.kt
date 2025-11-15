package com.group.i230535_i230048

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
// REMOVED: All Firebase imports
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val chatList: List<ChatSession>,
    private val currentUserId: String,
    private val onChatClick: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileImage: ImageView = itemView.findViewById(R.id.chat_avatar)
        val userName: TextView = itemView.findViewById(R.id.chat_user_name)
        val lastMessage: TextView = itemView.findViewById(R.id.chat_last_message)
        val timestamp: TextView = itemView.findViewById(R.id.chat_timestamp)

        fun bind(chat: ChatSession) {
            try {
                // 1. Get the other user's details directly from the model
                val otherUser = chat.getOtherUser(currentUserId)

                if (otherUser == null) {
                    userName.text = "Unknown User"
                    profileImage.setImageResource(R.drawable.circular_background)
                    Log.e("ChatAdapter", "Other user details are missing from ChatSession object")
                } else {
                    // 2. Set username
                    userName.text = otherUser.username

                    // 3. Load profile image using our migrated function
                    profileImage.loadUserAvatar(
                        uid = otherUser.uid,
                        fallbackUid = currentUserId,
                        placeholderRes = R.drawable.circular_background
                    )
                }

                // 4. Set last message
                if (chat.lastMessage.isNotEmpty()) {
                    val messagePrefix = if (chat.lastMessageSenderId == currentUserId) "You: " else ""
                    lastMessage.text = messagePrefix + chat.lastMessage
                } else {
                    lastMessage.text = "No messages yet"
                }

                // 5. Format and display timestamp
                timestamp.text = formatTimestamp(chat.lastMessageTimestamp)

                // 6. Set click listener
                itemView.setOnClickListener {
                    onChatClick(chat)
                }

            } catch (e: Exception) {
                Log.e("ChatAdapter", "Error in bind: ${e.message}", e)
            }
        }

        // REMOVED: The entire 'loadUserDetails' function is no longer needed.

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp == 0L) return ""
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "Just now"
                diff < 3_600_000 -> "${diff / 60_000}m"
                diff < 86_400_000 -> "${diff / 3_600_000}h"
                diff < 604_800_000 -> "${diff / 86_400_000}d"
                else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chatList[position])
    }

    override fun getItemCount(): Int = chatList.size
}