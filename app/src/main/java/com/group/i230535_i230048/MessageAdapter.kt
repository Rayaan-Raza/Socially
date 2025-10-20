package com.group.i230535_i230048

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// Message Adapter for RecyclerView
class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String,
    private val onMessageLongClick: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timeText: TextView = view.findViewById(R.id.messageTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == 1) {
            R.layout.item_message_sent
        } else {
            R.layout.item_message_received
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // Set message content
        holder.messageText.text = message.getDisplayContent()

        // Set timestamp
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val time = sdf.format(Date(message.timestamp))
        val timeText = if (message.isEdited) "$time (edited)" else time
        holder.timeText.text = timeText

        // Long press to edit/delete
        holder.itemView.setOnLongClickListener {
            onMessageLongClick(message)
            true
        }
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        // Return 1 for sent messages, 0 for received
        return if (messages[position].senderId == currentUserId) 1 else 0
    }
}