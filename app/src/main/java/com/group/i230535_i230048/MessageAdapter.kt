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

class MessageAdapter(
    private val messages: List<Message>,
    private val currentUserId: String,
    private val onMessageLongClick: (Message) -> Unit // Callback for long press
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    // --- View Type Constants ---
    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_RECEIVED = 2
        // --- MODIFIED: Re-add IMAGE types ---
        private const val VIEW_TYPE_IMAGE_SENT = 3
        private const val VIEW_TYPE_IMAGE_RECEIVED = 4
        // ---
        private const val VIEW_TYPE_POST_SENT = 5
        private const val VIEW_TYPE_POST_RECEIVED = 6
    }

    // --- Determine View Type ---
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isSent = message.senderId == currentUserId

        if (message.isDeleted) {
            return if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }

        // --- MODIFIED: Route "image" to new types ---
        return when (message.messageType) {
            "text" -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
            "image" -> if (isSent) VIEW_TYPE_IMAGE_SENT else VIEW_TYPE_IMAGE_RECEIVED // <-- CHANGED
            "post" -> if (isSent) VIEW_TYPE_POST_SENT else VIEW_TYPE_POST_RECEIVED
            else -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }
    }

    // --- Inflate Layout Based on View Type ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        // --- MODIFIED: Add cases for new image layouts ---
        val layoutId = when (viewType) {
            VIEW_TYPE_TEXT_SENT -> R.layout.item_message_sent
            VIEW_TYPE_TEXT_RECEIVED -> R.layout.item_message_received
            VIEW_TYPE_IMAGE_SENT -> R.layout.item_image_sent // <-- ADDED
            VIEW_TYPE_IMAGE_RECEIVED -> R.layout.item_image_received // <-- ADDED
            VIEW_TYPE_POST_SENT -> R.layout.item_post_sent
            VIEW_TYPE_POST_RECEIVED -> R.layout.item_post_received
            else -> R.layout.item_message_sent // Fallback
        }
        val view = layoutInflater.inflate(layoutId, parent, false)
        return MessageViewHolder(view, viewType, onMessageLongClick)
    }

    // --- Bind Data to ViewHolder ---
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    // --- ViewHolder Class ---
    class MessageViewHolder(
        itemView: View,
        private val viewType: Int,
        private val onItemLongClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        // Find views - use nullable types '?'
        private val messageText: TextView? = itemView.findViewById(R.id.messageText)
        private val timeText: TextView? = itemView.findViewById(R.id.messageTime)

        // messageImage is now used for BOTH posts and images
        private val messageImage: ImageView? = itemView.findViewById(R.id.messageImage)
        private val postIndicator: TextView? = itemView.findViewById(R.id.postIndicator)

        fun bind(message: Message) {
            // Handle common elements
            setTimeText(message)

            // Handle specific view types
            when (viewType) {
                VIEW_TYPE_TEXT_SENT, VIEW_TYPE_TEXT_RECEIVED -> {
                    messageText?.text = message.getDisplayContent()
                    messageImage?.visibility = View.GONE
                    postIndicator?.visibility = View.GONE
                    setupLongClickListener(message)
                }

                // --- MODIFIED: Add block for image types ---
                VIEW_TYPE_IMAGE_SENT, VIEW_TYPE_IMAGE_RECEIVED -> {
                    messageText?.visibility = View.GONE // Hide text
                    postIndicator?.visibility = View.GONE // Hide post indicator
                    messageImage?.visibility = View.VISIBLE // Show image

                    messageImage?.let { imgView ->
                        // Use your existing helper to load the Base64 image
                        // (This assumes your ChatActivity sends Base64 in message.imageUrl)
                        decodeAndLoadBase64(message.imageUrl, imgView)
                    }

                    // Allow long-clicking on images to delete them
                    setupLongClickListener(message)
                }
                // ---

                VIEW_TYPE_POST_SENT, VIEW_TYPE_POST_RECEIVED -> {
                    messageText?.visibility = View.GONE
                    postIndicator?.visibility = View.VISIBLE
                    messageImage?.visibility = View.VISIBLE
                    messageImage?.let { imgView ->
                        loadPostImage(message.imageUrl, imgView)

                        imgView.setOnClickListener {
                            if (message.postId.isNotEmpty() && message.senderId.isNotEmpty()) {
                                // --- FIX: Use message.senderId (the post owner), not message.receiverId ---
                                openPostDetails(itemView.context, message.postId, message.senderId)
                            } else {
                                Log.w("MessageAdapter", "Missing postId or senderId for shared post message.")
                                Toast.makeText(itemView.context, "Cannot open post.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
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
            // --- MODIFIED: Allow long click on "text" OR "image" ---
            if (!message.isDeleted && (message.messageType == "text" || message.messageType == "image")) {
                itemView.setOnLongClickListener {
                    onItemLongClick(message)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }

        // Decode Base64 String and load into ImageView
        private fun decodeAndLoadBase64(base64String: String, imageView: ImageView) {
            if (base64String.isBlank()) {
                imageView.setImageResource(R.drawable.city) // Your placeholder
                return
            }
            try {
                // Clean the Base64 string (remove prefix if it exists)
                val cleanBase64 = base64String.substringAfter("base64,", base64String)
                val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                } else {
                    imageView.setImageResource(R.drawable.city) // Your placeholder
                }
            } catch (e: Exception) {
                Log.e("MessageAdapter", "Error decoding Base64", e)
                imageView.setImageResource(R.drawable.city) // Your placeholder
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
                putExtra("USER_ID", postOwnerId)
            }
            context.startActivity(intent)
        }
    }

    // --- Add this helper function inside MessageAdapter, outside the ViewHolder ---
    // This provides the text for text-based views (handling deleted, images, etc.)
    private fun Message.getDisplayContent(): String {
        return when {
            isDeleted -> "This message was deleted"
            messageType == "image" -> "📷 Photo" // Fallback text if it's routed to text
            else -> content
        }
    }
}