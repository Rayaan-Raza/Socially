package com.group.i230535_i230048

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth

class FollowerAdapter(
    private val followers: List<User>, // Assuming User data class exists
    private val onFollowerClick: (User) -> Unit
) : RecyclerView.Adapter<FollowerAdapter.FollowerVH>() {

    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    inner class FollowerVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: ImageView = itemView.findViewById(R.id.userAvatar)
        val username: TextView = itemView.findViewById(R.id.username)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowerVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_select, parent, false)
        return FollowerVH(view)
    }

    override fun onBindViewHolder(holder: FollowerVH, position: Int) {
        val user = followers[position]
        holder.username.text = user.username
        // Use your existing extension function or Glide directly
        holder.avatar.loadUserAvatar(user.uid, myUid, R.drawable.oval)

        holder.itemView.setOnClickListener {
            onFollowerClick(user)
        }
    }

    override fun getItemCount(): Int = followers.size
}