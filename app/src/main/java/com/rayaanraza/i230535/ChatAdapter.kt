package com.rayaanraza.i230535

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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

    // --- ViewHolder remains the same ---
    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.chat_user_name) // Make sure this ID is correct in item_chat.xml
        val lastMessage: TextView = itemView.findViewById(R.id.chat_last_message)
        val timestamp: TextView = itemView.findViewById(R.id.chat_timestamp)
        val avatar: TextView = itemView.findViewById(R.id.chat_avatar)
    }

    // In ChatAdapter.kt

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        // CHANGE THIS LINE: Use the new, correct layout file.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dms_conversation, parent, false) // <-- THE FIX
        return ChatViewHolder(view)
    }


    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatSession = chatList[position]

        // --- THIS IS THE ROBUST LOGIC ---
        // 1. Find the other user's ID
        val otherUserId = chatSession.participants.keys.firstOrNull { it != currentUserId }

        if (otherUserId == null) {
            // Handle error case where the other participant is missing
            holder.userName.text = "Unknown Chat"
            holder.lastMessage.text = "Error: Participant not found."
            holder.avatar.text = "?"
            return // Stop further execution for this item
        }

        // 2. Set static data immediately
        holder.lastMessage.text = chatSession.lastMessage
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        holder.timestamp.text = if (chatSession.lastMessageTimestamp > 0) sdf.format(Date(chatSession.lastMessageTimestamp)) else ""

        // 3. Fetch the other user's data from the "/users" node
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(otherUserId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // IMPORTANT: Check if the holder is still valid for this position.
                // This prevents crashes if the user scrolls quickly.
                if (holder.adapterPosition == RecyclerView.NO_POSITION) {
                    return
                }

                // Fetch the user's name from the database.
                val otherUserName = snapshot.child("username").getValue(String::class.java) ?: "Unknown User"

                // Update the UI with the fetched data
                holder.userName.text = otherUserName

                // Set avatar text from the fetched username
                holder.avatar.text = if (otherUserName.isNotEmpty() && otherUserName != "Unknown User") {
                    otherUserName.split(" ")
                        .take(2).mapNotNull { it.firstOrNull()?.toString()?.uppercase() }.joinToString("")
                } else {
                    "?"
                }

                // 4. Set the click listener AFTER we have all the data
                holder.itemView.setOnClickListener {
                    val context = holder.itemView.context
                    val intent = Intent(context, chat::class.java).apply {
                        putExtra("userId", otherUserId)
                        putExtra("username", otherUserName)
                    }
                    context.startActivity(intent)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle the case where we can't fetch the user's data
                if (holder.adapterPosition != RecyclerView.NO_POSITION) {
                    holder.userName.text = "Error Loading Name"
                    holder.avatar.text = "!"
                }
            }
        })
    }

    override fun getItemCount() = chatList.size
}
