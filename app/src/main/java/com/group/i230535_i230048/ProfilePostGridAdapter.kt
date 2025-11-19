package com.group.i230535_i230048

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapter for displaying posts in a grid layout on profile screens.
 * Uses Glide to efficiently load URLs or Base64 bytes directly.
 */
class ProfilePostGridAdapter(
    private val posts: List<Post>,
    private val onPostClick: (post: Post) -> Unit
) : RecyclerView.Adapter<ProfilePostGridAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_grid, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val ctx = holder.itemView.context
        val iv = holder.postImage

        // 1. Check for URL
        val url = post.imageUrl.takeIf { it.isNotEmpty() && it.startsWith("http", true) }

        // 2. Check for Base64 (optimistic or persistent)
        val b64 = if (post.imageBase64.isNotEmpty()) post.imageBase64
        else if (post.imageUrl.isNotEmpty() && !post.imageUrl.startsWith("http")) post.imageUrl
        else null

        if (url != null) {
            // URL Load
            Glide.with(ctx)
                .load(url)
                .thumbnail(0.25f)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .centerCrop()
                .into(iv)
        } else if (b64 != null) {
            // Base64 Load (Using Glide to handle bytes is much smoother than manual decoding)
            try {
                val clean = b64.substringAfter("base64,", b64)
                val bytes = Base64.decode(clean, Base64.DEFAULT)

                Glide.with(ctx)
                    .load(bytes)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.placeholder_image)
                    .centerCrop()
                    .into(iv)
            } catch (e: Exception) {
                iv.setImageResource(R.drawable.placeholder_image)
            }
        } else {
            // No image
            iv.setImageResource(R.drawable.placeholder_image)
        }

        holder.itemView.setOnClickListener { onPostClick(post) }
    }

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        // Clear Glide target to prevent memory leaks and wrong images
        Glide.with(holder.itemView.context).clear(holder.postImage)
    }

    override fun getItemCount() = posts.size
}