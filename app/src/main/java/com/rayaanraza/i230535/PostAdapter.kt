package com.rayaanraza.i230535

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PostAdapter(
    private val onLikeToggle: (postId: String, liked: Boolean) -> Unit,
    private val onCommentClick: (postId: String) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostVH>() {

    private val items = mutableListOf<Post>()
    private val likeState = mutableMapOf<String, Boolean>()             // postId -> did I like
    private val likeCounts = mutableMapOf<String, Int>()                // postId -> count
    private val commentPreviews = mutableMapOf<String, List<Comment>>() // postId -> latest 2
    private val commentTotals = mutableMapOf<String, Int>()             // postId -> total count

    fun submitList(list: List<Post>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setLikeCount(postId: String, count: Int) {
        likeCounts[postId] = count
        val idx = items.indexOfFirst { it.postId == postId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun setLiked(postId: String, liked: Boolean) {
        likeState[postId] = liked
        val idx = items.indexOfFirst { it.postId == postId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun setCommentPreview(postId: String, comments: List<Comment>) {
        commentPreviews[postId] = comments
        val idx = items.indexOfFirst { it.postId == postId }
        if (idx >= 0) notifyItemChanged(idx)
    }

    fun setCommentTotal(postId: String, total: Int) {
        commentTotals[postId] = total
        val idx = items.indexOfFirst { it.postId == postId }
        if (idx >= 0) notifyItemChanged(idx)
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

    override fun onBindViewHolder(h: PostVH, position: Int) {
        val item = items[position]

        // Username / caption
        h.username.text = item.username.ifEmpty { "user" }
        h.tvCaption.text = "${item.username}  ${item.caption}"

        // Avatar placeholder
        h.avatar.setImageResource(R.drawable.oval)

        // Post image (Firebase URL or fallback)
        if (!item.imageUrl.isNullOrEmpty()) {
            Glide.with(h.postImage.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.person1)
                .error(R.drawable.person1)
                .into(h.postImage)
        } else {
            h.postImage.setImageResource(R.drawable.person1)
        }

        // Likes
        val liked = likeState[item.postId] == true
        h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
        val count = likeCounts[item.postId] ?: item.likeCount.toInt()
        h.tvLikes.text = if (count == 1) "1 like" else "$count likes"

        // Comments (up to 2)
        val previews = commentPreviews[item.postId] ?: emptyList()
        if (previews.isNotEmpty()) {
            h.tvC1.visibility = View.VISIBLE
            h.tvC1.text = "${previews[0].username}: ${previews[0].text}"
        } else {
            h.tvC1.visibility = View.GONE
            h.tvC1.text = ""
        }

        if (previews.size >= 2) {
            h.tvC2.visibility = View.VISIBLE
            h.tvC2.text = "${previews[1].username}: ${previews[1].text}"
        } else {
            h.tvC2.visibility = View.GONE
            h.tvC2.text = ""
        }

        // "View all" visible if total comments > 2
        val total = commentTotals[item.postId] ?: previews.size
        h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

        // Like button click
        h.likeBtn.setOnClickListener {
            val currentlyLiked = likeState[item.postId] == true
            onLikeToggle(item.postId, !currentlyLiked)
        }

        // Comment clicks
        h.commentBtn.setOnClickListener { onCommentClick(item.postId) }
        h.tvViewAll.setOnClickListener { onCommentClick(item.postId) }
    }

    override fun getItemCount() = items.size

    private fun decodeBase64(b64: String?): Bitmap? {
        return try {
            if (b64.isNullOrEmpty()) null
            else {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }
}
