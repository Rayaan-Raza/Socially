package com.rayaanraza.i230535

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PostAdapter(
    private val onLikeToggle: (postId: String, liked: Boolean) -> Unit,
    private val onCommentClick: (postId: String) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostVH>() {

    private val items = mutableListOf<Post>()
    private val likeState = mutableMapOf<String, Boolean>()      // postId -> did I like
    private val likeCounts = mutableMapOf<String, Int>()          // postId -> count
    private val commentPreviews = mutableMapOf<String, List<Comment>>() // postId -> latest 2

    fun submitList(list: List<Post>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun setLikeCount(postId: String, count: Int) {
        likeCounts[postId] = count
        notifyItemChanged(items.indexOfFirst { it.postId == postId })
    }

    fun setLiked(postId: String, liked: Boolean) {
        likeState[postId] = liked
        notifyItemChanged(items.indexOfFirst { it.postId == postId })
    }

    fun setCommentPreview(postId: String, comments: List<Comment>) {
        commentPreviews[postId] = comments
        notifyItemChanged(items.indexOfFirst { it.postId == postId })
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

        // Avatar (optional: load from profiles later). Use default for now.
        h.avatar.setImageResource(R.drawable.oval)

        // Post image from Base64
        val bmp = decodeBase64(item.imageBase64)
        if (bmp != null) h.postImage.setImageBitmap(bmp) else h.postImage.setImageResource(R.drawable.person1)

        // Likes UI
        val liked = likeState[item.postId] == true
        h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
        val count = likeCounts[item.postId] ?: 0
        h.tvLikes.text = if (count == 1) "1 like" else "$count likes"

        // Comments (show up to 2)
        val previews = commentPreviews[item.postId] ?: emptyList()
        if (previews.isNotEmpty()) {
            h.tvC1.visibility = View.VISIBLE
            h.tvC1.text = "${previews[0].username}  ${previews[0].text}"
        } else h.tvC1.visibility = View.GONE

        if (previews.size >= 2) {
            h.tvC2.visibility = View.VISIBLE
            h.tvC2.text = "${previews[1].username}  ${previews[1].text}"
        } else h.tvC2.visibility = View.GONE

        h.tvViewAll.visibility = if ((countComments(previews) > 2)) View.VISIBLE else View.GONE

        // Like toggle
        h.likeBtn.setOnClickListener {
            val currentlyLiked = likeState[item.postId] == true
            onLikeToggle(item.postId, !currentlyLiked)
        }

        // Comment click
        h.commentBtn.setOnClickListener { onCommentClick(item.postId) }
        h.tvViewAll.setOnClickListener { onCommentClick(item.postId) }
    }

    private fun countComments(previews: List<Comment>): Int = previews.size

    override fun getItemCount() = items.size

    private fun decodeBase64(b64: String?): Bitmap? {
        return try {
            if (b64.isNullOrEmpty()) null
            else {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
    }
}
