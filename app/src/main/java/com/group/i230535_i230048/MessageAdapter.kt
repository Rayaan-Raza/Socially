package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Message Adapter for RecyclerView
class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String,
    private val onMessageLongClick: (Message) -> Unit // Callback for long press
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    // --- View Type Constants ---
    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_RECEIVED = 2
        // Removed IMAGE types
        private const val VIEW_TYPE_POST_SENT = 5        // Kept Post type
        private const val VIEW_TYPE_POST_RECEIVED = 6     // Kept Post type
    }

    // --- Determine View Type ---
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isSent = message.senderId == currentUserId

        // Handle deleted messages primarily as text
        if (message.isDeleted) {
            return if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }

        // Map based on type
        return when (message.messageType) {
            "text" -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
            "image" -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED // Treat images as text for display
            "post" -> if (isSent) VIEW_TYPE_POST_SENT else VIEW_TYPE_POST_RECEIVED
            else -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED // Fallback
        }
    }

    // --- Inflate Layout Based on View Type ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        // Determine the layout resource ID based on the view type
        val layoutId = when (viewType) {
            VIEW_TYPE_TEXT_SENT -> R.layout.item_message_sent
            VIEW_TYPE_TEXT_RECEIVED -> R.layout.item_message_received
            VIEW_TYPE_POST_SENT -> R.layout.item_post_sent // Use NEW post layout
            VIEW_TYPE_POST_RECEIVED -> R.layout.item_post_received // Use NEW post layout
            else -> R.layout.item_message_sent // Fallback
        }
        val view = layoutInflater.inflate(layoutId, parent, false)
        // Pass the viewType to the ViewHolder
        return MessageViewHolder(view, viewType, onMessageLongClick)
    }

    // --- Bind Data to ViewHolder ---
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position]) // Delegate binding logic to ViewHolder
    }

    override fun getItemCount() = messages.size

    // --- ViewHolder Class ---
    class MessageViewHolder(
        itemView: View,
        private val viewType: Int, // Store viewType to know which views to access
        private val onItemLongClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        // Find views - use nullable types '?'
        private val messageText: TextView? = itemView.findViewById(R.id.messageText)
        private val timeText: TextView? = itemView.findViewById(R.id.messageTime) // Assume exists in all
        
        // Views specific to post layouts
        private val messageImage: ImageView? = itemView.findViewById(R.id.messageImage)
        private val postIndicator: TextView? = itemView.findViewById(R.id.postIndicator)

        // Bind data based on message and viewType
        fun bind(message: Message) {
            // --- Handle common elements ---
            setTimeText(message) // Set formatted time and edited status

            // --- Handle specific view types ---
            when (viewType) {
                VIEW_TYPE_TEXT_SENT, VIEW_TYPE_TEXT_RECEIVED -> {
                    // Display text content (handles deleted, image placeholders)
                    messageText?.text = message.getDisplayContent()
                    messageImage?.visibility = View.GONE // Ensure image view is hidden
                    postIndicator?.visibility = View.GONE

                    // Set long click listener only for non-deleted messages
                    setupLongClickListener(message)
                }

                VIEW_TYPE_POST_SENT, VIEW_TYPE_POST_RECEIVED -> {
                    messageText?.visibility = View.GONE // Hide text view if it exists in this layout
                    postIndicator?.visibility = View.VISIBLE // Show "Shared Post" indicator
                    messageImage?.visibility = View.VISIBLE // Show image view
                    messageImage?.let { imgView ->
                        loadPostImage(message.imageUrl, imgView) // Load post image (URL or Base64)

                        // Make post image clickable -> opens GotoPostActivity
                        imgView.setOnClickListener {
                            // Use postId and senderId (post owner)
                            if (message.postId.isNotEmpty() && message.senderId.isNotEmpty()) {
                                openPostDetails(itemView.context, message.postId, message.senderId)
                            } else {
                                Log.w("MessageAdapter", "Missing postId or senderId for shared post message.")
                                Toast.makeText(itemView.context, "Cannot open post.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    // Disable long click for posts
                    itemView.setOnLongClickListener(null)
                }
            }
        }

        // --- Helper Functions within ViewHolder ---

        private fun setTimeText(message: Message) {
            timeText?.let {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val time = try {
                    sdf.format(Date(message.timestamp))
                } catch (e: Exception) { "--:--" }
                val displayText = if (message.isEdited && !message.isDeleted) "$time (edited)" else time
                it.text = displayText
            }
        }

        private fun setupLongClickListener(message: Message) {
            if (!message.isDeleted && message.messageType == "text") { // Only allow long click on deletable/editable TEXT
                itemView.setOnLongClickListener {
                    onItemLongClick(message)
                    true // Consume the long click event
                }
            } else {
                itemView.setOnLongClickListener(null) // Remove listener otherwise
            }
        }

        // Decode Base64 String and load into ImageView
        private fun decodeAndLoadBase64(base64String: String, imageView: ImageView) {
            if (base64String.isBlank()) {
                imageView.setImageResource(R.drawable.city)
                return
            }
            try {
                val cleanBase64 = base64String.substringAfter("base64,", base64String)
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.drawable.city)
                }
            } catch (e: Exception) {
                Log.e("MessageAdapter", "Error decoding Base64", e)
                imageView.setImageResource(R.drawable.city)
            }
        }

        // Load image for Post (handles URL or Base64)
        private fun loadPostImage(imageUrlOrBase64: String, imageView: ImageView) {
            if (imageUrlOrBase64.isBlank()) {
                imageView.setImageResource(R.drawable.city)
                return
            }
            if (imageUrlOrBase64.startsWith("http", ignoreCase = true)) {
                Glide.with(itemView.context)
                    .load(imageUrlOrBase64)
                    .placeholder(R.drawable.city)
                    .error(R.drawable.city)
                    .into(imageView)
            } else {
                decodeAndLoadBase64(imageUrlOrBase64, imageView)
            }
        }

        // Navigate to GotoPostActivity
        private fun openPostDetails(context: Context, postId: String, postOwnerId: String) {
            val intent = Intent(context, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", postId)
                putExtra("USER_ID", postOwnerId) // Pass the owner's ID
            }
            context.startActivity(intent)
        }
    }
}