package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // CHANGED
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchAdapter(private val context: Context, private val userList: List<User>) :
    RecyclerView.Adapter<SearchAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // --- CHANGED: Corrected to ImageView ---
        val avatar: ImageView = itemView.findViewById(R.id.user_avatar)
        // ---
        val username: TextView = itemView.findViewById(R.id.user_username)
        val fullName: TextView = itemView.findViewById(R.id.user_fullname)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.username.text = user.username
        holder.fullName.text = user.fullName

        // --- CHANGED: Load profile picture ---
        // This uses our migrated function to load from the local DB
        holder.avatar.loadUserAvatar(user.uid, user.uid, R.drawable.default_avatar)
        // ---

        // (Click listener is correct, no changes)
        holder.itemView.setOnClickListener {
            val intent = Intent(context, view_profile::class.java).apply {
                putExtra("USER_ID", user.uid)
            }
            context.startActivity(intent)
        }
    }
}