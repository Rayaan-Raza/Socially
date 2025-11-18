package com.group.i230535_i230048

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Adapter for displaying posts in a grid layout on profile screens.
 * Efficiently handles both URL and Base64 images with proper recycling.
 *
 * Note: This adapter doesn't make API calls - it displays Post objects
 * that should be loaded from the database or API by the parent Activity/Fragment.
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

        val url = post.imageUrl.takeIf { it.isNotEmpty() && it.startsWith("http", true) }
        val b64 = post.imageBase64

        if (!url.isNullOrEmpty()) {
            // URL image - straightforward Glide loading
            Glide.with(ctx)
                .load(url)
                .thumbnail(0.25f)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.placeholder_image)
                .centerCrop()
                .into(iv)
        } else if (b64.isNotEmpty()) {
            // Base64 image - decode off main thread, then let Glide handle rendering
            (ctx as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                val bytes = withContext(Dispatchers.IO) {
                    try {
                        // Handle optional data URI prefix (e.g., "data:image/jpeg;base64,...")
                        val clean = b64.substringAfter("base64,", b64)
                        android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                    } catch (_: Exception) { null }
                }
                if (bytes != null) {
                    Glide.with(ctx)
                        .load(bytes)
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.placeholder_image)
                        .error(R.drawable.placeholder_image)
                        .centerCrop()
                        .into(iv)
                } else {
                    iv.setImageResource(R.drawable.placeholder_image)
                }
            } ?: run {
                // Fallback if context is not an AppCompatActivity
                iv.setImageResource(R.drawable.placeholder_image)
            }
        } else {
            // No image available
            iv.setImageResource(R.drawable.placeholder_image)
        }

        holder.itemView.setOnClickListener { onPostClick(post) }
    }

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        // Prevent wrong images flashing when views are reused
        Glide.with(holder.itemView.context).clear(holder.postImage)
        holder.postImage.setImageDrawable(null)
    }

    override fun getItemCount() = posts.size
}