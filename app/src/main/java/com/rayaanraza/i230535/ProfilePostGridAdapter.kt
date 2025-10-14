package com.rayaanraza.i230535

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// This adapter is designed for a simple grid view, like on a profile page.
// It takes a list of Post objects but only uses their image URLs.
class ProfilePostGridAdapter(
    private val posts: List<Post>,
    private val onPostClick: (post: Post) -> Unit
) : RecyclerView.Adapter<ProfilePostGridAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Assumes your item_post_grid.xml has an ImageView with this ID
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        // Ensure you have a layout file named `item_post_grid.xml`
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_grid, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]

        // Load the actual image URL using Glide
        Glide.with(holder.itemView.context)
            .load(post.imageUrl)
            .placeholder(R.drawable.placeholder_image) // Make sure you have this drawable
            .error(R.drawable.placeholder_image)
            .centerCrop()
            .into(holder.postImage)

        holder.itemView.setOnClickListener {
            onPostClick(post)
        }
    }

    override fun getItemCount() = posts.size
}