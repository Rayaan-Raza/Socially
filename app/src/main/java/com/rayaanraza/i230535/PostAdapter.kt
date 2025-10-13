package com.rayaanraza.i230535

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// A simple adapter for displaying placeholder posts in a grid
class PostAdapter(private val posts: List<String>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Assuming your item layout has an ImageView
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        // You will need a layout file `item_post_grid.xml`
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_post_grid, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        // For now, we just set a placeholder image for every item
        // In the future, you would load the actual image URL here using Glide or Picasso
        holder.postImage.setImageResource(R.drawable.placeholder_image) // Make sure you have this drawable
    }

    override fun getItemCount() = posts.size
}
