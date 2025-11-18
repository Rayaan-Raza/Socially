package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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

// NOTE: These data classes are required for the adapter to work.
// Place them in this file or in a separate "Models" file.


data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val uid: String = "",
    val username: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

/**
 * This adapter is designed for the main feed, showing full post details.
 * It connects to a layout like `item_post.xml`.
 */
class PostFeedAdapter(
    private val onLikeToggle: (post: Post, liked: Boolean) -> Unit,
    private val onCommentClick: (post: Post) -> Unit
) : RecyclerView.Adapter<PostFeedAdapter.PostVH>() {

    private val items = mutableListOf<Post>()
    private val usernameCache = mutableMapOf<String, String>()
    private val likeState = mutableMapOf<String, Boolean>()      // postId -> I liked
    private val likeCounts = mutableMapOf<String, Int>()         // postId -> total likes
    private val commentPreviews = mutableMapOf<String, List<Comment>>() // postId -> 2 latest comments
    private val commentTotals = mutableMapOf<String, Int>()      // postId -> total comments

    fun submitList(list: List<Post>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setLikeCount(postId: String, count: Int) {
        likeCounts[postId] = count
        items.indexOfFirst { it.postId == postId }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    fun setLiked(postId: String, liked: Boolean) {
        likeState[postId] = liked
        items.indexOfFirst { it.postId == postId }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    fun setCommentPreview(postId: String, comments: List<Comment>) {
        commentPreviews[postId] = comments
        items.indexOfFirst { it.postId == postId }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    fun setCommentTotal(postId: String, total: Int) {
        commentTotals[postId] = total
        items.indexOfFirst { it.postId == postId }.takeIf { it != -1 }?.let { notifyItemChanged(it) }
    }

    inner class PostVH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.imgAvatar)
        val username: TextView = v.findViewById(R.id.tvUsername)
        val postImage: ImageView = v.findViewById(R.id.imgPost)
        val likeBtn: ImageView = v.findViewById(R.id.btnLike)
        val tvLikes: TextView = v.findViewById(R.id.tvLikes)
        val tvCaption: TextView = v.findViewById(R.id.tvCaption)
        val tvC1: TextView = v.findViewById(R.id.tvComment1)
        val tvC2: TextView = v.findViewById(R.id.tvComment2)
        val tvViewAll: TextView = v.findViewById(R.id.tvViewAll)
        val commentBtn: ImageView = v.findViewById(R.id.btnComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
        return PostVH(v)
    }

    @SuppressLint("SetTextI18n", "RecyclerView")
    override fun onBindViewHolder(h: PostVH, position: Int) {
        val item = items[position]

        // --- Username and Caption ---
        val shownName = usernameCache[item.uid] ?: item.username.takeIf { it.isNotBlank() } ?: "user"
        h.username.text = shownName
        h.tvCaption.text = "$shownName  ${item.caption}"

        // --- Avatar ---
        // Using the loadUserAvatar extension function
        h.avatar.loadUserAvatar(item.uid, item.uid, R.drawable.oval)

        // --- Post Image (URL preferred, Base64 as fallback) ---
        if (item.imageUrl.isNotEmpty()) {
            Glide.with(h.postImage.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.person1)
                .error(R.drawable.person1)
                .into(h.postImage)
        } else if (item.imageBase64.isNotEmpty()) {
            val bmp = decodeBase64(item.imageBase64)
            if (bmp != null) h.postImage.setImageBitmap(bmp) else h.postImage.setImageResource(R.drawable.person1)
        } else {
            h.postImage.setImageResource(R.drawable.person1)
        }

        // --- Likes ---
        val liked = likeState[item.postId] == true
        h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
        val liveCount = likeCounts[item.postId] ?: item.likeCount.toInt()
        h.tvLikes.text = if (liveCount == 1) "1 like" else "$liveCount likes"

        // --- Comment previews ---
        val previews = commentPreviews[item.postId] ?: emptyList()
        h.tvC1.visibility = if (previews.isNotEmpty()) View.VISIBLE else View.GONE
        if (previews.isNotEmpty()) h.tvC1.text = "${previews[0].username}: ${previews[0].text}"

        h.tvC2.visibility = if (previews.size >= 2) View.VISIBLE else View.GONE
        if (previews.size >= 2) h.tvC2.text = "${previews[1].username}: ${previews[1].text}"

        // --- "View all" link ---
        val total = commentTotals[item.postId] ?: item.commentCount.toInt()
        h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

        // --- Click Listeners ---
        h.likeBtn.setOnClickListener {
            onLikeToggle(item, !liked)
        }
        h.commentBtn.setOnClickListener { onCommentClick(item) }
        h.tvViewAll.setOnClickListener { onCommentClick(item) }
    }

    override fun getItemCount() = items.size

    private fun decodeBase64(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}