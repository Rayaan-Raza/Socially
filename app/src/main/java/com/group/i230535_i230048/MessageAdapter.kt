package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
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
    private val onMessageLongClick: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TEXT_SENT = 1
        private const val VIEW_TYPE_TEXT_RECEIVED = 2
        private const val VIEW_TYPE_IMAGE_SENT = 3
        private const val VIEW_TYPE_IMAGE_RECEIVED = 4
        private const val VIEW_TYPE_POST_SENT = 5
        private const val VIEW_TYPE_POST_RECEIVED = 6
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        val isSent = message.senderId == currentUserId

        if (message.isDeleted) {
            return if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }

        return when (message.messageType) {
            "text" -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
            "image" -> if (isSent) VIEW_TYPE_IMAGE_SENT else VIEW_TYPE_IMAGE_RECEIVED
            "post" -> if (isSent) VIEW_TYPE_POST_SENT else VIEW_TYPE_POST_RECEIVED
            else -> if (isSent) VIEW_TYPE_TEXT_SENT else VIEW_TYPE_TEXT_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val layoutId = when (viewType) {
            VIEW_TYPE_TEXT_SENT -> R.layout.item_message_sent
            VIEW_TYPE_TEXT_RECEIVED -> R.layout.item_message_received
            VIEW_TYPE_IMAGE_SENT -> R.layout.item_image_sent
            VIEW_TYPE_IMAGE_RECEIVED -> R.layout.item_image_received
            VIEW_TYPE_POST_SENT -> R.layout.item_post_sent
            VIEW_TYPE_POST_RECEIVED -> R.layout.item_post_received
            else -> R.layout.item_message_sent
        }
        val view = layoutInflater.inflate(layoutId, parent, false)
        return MessageViewHolder(view, viewType, onMessageLongClick)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(
        itemView: View,
        private val viewType: Int,
        private val onItemLongClick: (Message) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val messageText: TextView? = itemView.findViewById(R.id.messageText)
        private val timeText: TextView? = itemView.findViewById(R.id.messageTime)
        private val messageImage: ImageView? = itemView.findViewById(R.id.messageImage)
        private val postIndicator: TextView? = itemView.findViewById(R.id.postIndicator)

        fun bind(message: Message) {
            setTimeText(message)

            when (viewType) {
                VIEW_TYPE_TEXT_SENT, VIEW_TYPE_TEXT_RECEIVED -> {
                    messageText?.text = message.getDisplayContent()
                    messageImage?.visibility = View.GONE
                    postIndicator?.visibility = View.GONE
                    setupLongClickListener(message)
                }

                VIEW_TYPE_IMAGE_SENT, VIEW_TYPE_IMAGE_RECEIVED -> {
                    messageText?.visibility = View.GONE
                    postIndicator?.visibility = View.GONE
                    messageImage?.visibility = View.VISIBLE

                    messageImage?.let { imgView ->
                        // Load using smart helper (handles URL vs Base64)
                        loadSmartImage(itemView.context, message.imageUrl, imgView)
                    }
                    setupLongClickListener(message)
                }

                VIEW_TYPE_POST_SENT, VIEW_TYPE_POST_RECEIVED -> {
                    messageText?.visibility = View.GONE
                    postIndicator?.visibility = View.VISIBLE
                    messageImage?.visibility = View.VISIBLE

                    messageImage?.let { imgView ->
                        loadSmartImage(itemView.context, message.imageUrl, imgView)

                        imgView.setOnClickListener {
                            if (message.postId.isNotEmpty()) {
                                // Open post using the SenderID (Post Owner) if available,
                                // otherwise fallback to current message sender
                                val targetUid = if(message.senderId.isNotEmpty()) message.senderId else ""
                                openPostDetails(itemView.context, message.postId, targetUid)
                            } else {
                                Toast.makeText(itemView.context, "Cannot open post.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    itemView.setOnLongClickListener(null)
                }
            }
        }

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
            if (!message.isDeleted && (message.messageType == "text" || message.messageType == "image")) {
                itemView.setOnLongClickListener {
                    onItemLongClick(message)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }

        /**
         * Smart Image Loader:
         * 1. Checks if string is a Web URL.
         * 2. If not, assumes Base64, decodes to bytes, and lets Glide load it efficiently.
         */
        private fun loadSmartImage(context: Context, source: String, imageView: ImageView) {
            if (source.isBlank()) {
                imageView.setImageResource(R.drawable.city) // Placeholder
                return
            }

            if (source.startsWith("http", ignoreCase = true)) {
                // It is a Server URL
                Glide.with(context)
                    .load(source)
                    .placeholder(R.drawable.city)
                    .error(R.drawable.city)
                    .into(imageView)
            } else {
                // It is Base64 (Optimistic Send)
                try {
                    val cleanBase64 = source.substringAfter("base64,", source)
                    val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                    Glide.with(context)
                        .load(imageBytes) // Glide can load raw bytes!
                        .placeholder(R.drawable.city)
                        .error(R.drawable.city)
                        .into(imageView)
                } catch (e: Exception) {
                    Log.e("MessageAdapter", "Base64 error", e)
                    imageView.setImageResource(R.drawable.city)
                }
            }
        }

        private fun openPostDetails(context: Context, postId: String, postOwnerId: String) {
            val intent = Intent(context, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", postId)
                putExtra("USER_ID", postOwnerId)
            }
            context.startActivity(intent)
        }
    }

    private fun Message.getDisplayContent(): String {
        return when {
            isDeleted -> "This message was deleted"
            messageType == "image" -> "📷 Photo"
            else -> content
        }
    }
}