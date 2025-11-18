package com.group.i230535_i230048

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class StoryAdapter(
    private val items: List<StoryBubble>,
    private val currentUid: String
) : RecyclerView.Adapter<StoryAdapter.StoryVH>() {

    inner class StoryVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: TextView = itemView.findViewById(R.id.username)
        val pfp: ImageView = itemView.findViewById(R.id.pfp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_story_bubble, parent, false)
        return StoryVH(view)
    }

    override fun onBindViewHolder(holder: StoryVH, position: Int) {
        try {
            val item = items[position]

            // Determine if this is the user's own story bubble
            val isSelfBubble = (position == 0) || (item.uid == currentUid)

            // Set username text
            holder.username.text = if (isSelfBubble) {
                "Your Story"
            } else {
                item.username?.ifBlank { "User" } ?: "User"
            }

            // Determine which UID to load avatar for
            val targetUid = if (isSelfBubble) currentUid else item.uid

            // Load avatar with error handling
            try {
                holder.pfp.loadUserAvatar(
                    uid = targetUid,
                    fallbackUid = currentUid,
                    placeholderRes = R.drawable.person1
                )
            } catch (e: Exception) {
                Log.e("StoryAdapter", "Error loading avatar: ${e.message}")
                holder.pfp.setImageResource(R.drawable.person1)
            }

            // Click listener to open story viewer
            holder.itemView.setOnClickListener {
                try {
                    // Use the actual UID, or fallback to currentUid if empty
                    val safeUid = if (item.uid.isNotBlank()) item.uid else currentUid

                    val intent = Intent(holder.itemView.context, camera_story::class.java)
                    intent.putExtra("uid", safeUid)
                    holder.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("StoryAdapter", "Error opening story: ${e.message}")
                    Toast.makeText(
                        holder.itemView.context,
                        "Unable to open story",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("StoryAdapter", "Error binding story at position $position: ${e.message}")
            // Set safe defaults
            holder.username.text = "User"
            holder.pfp.setImageResource(R.drawable.person1)
        }
    }

    override fun getItemCount(): Int = items.size
}