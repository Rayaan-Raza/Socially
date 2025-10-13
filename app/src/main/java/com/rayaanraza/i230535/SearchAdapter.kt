// In SearchAdapter.kt
package com.rayaanraza.i230535

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rayaanraza.i230535.User // Make sure you have the top-level User data class

class SearchAdapter(private val context: Context, private val userList: List<User>) :
    RecyclerView.Adapter<SearchAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.user_avatar)
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

    // In SearchAdapter.kt

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.username.text = user.username
        holder.fullName.text = user.fullName

        // Set initials for avatar
        holder.avatar.text = user.fullName.split(" ")
            .take(2).mapNotNull { it.firstOrNull()?.toString()?.uppercase() }.joinToString("")

        // --- THIS IS THE CRUCIAL CHANGE ---
        // Set click listener to open the view_profile activity with the selected user's ID
        holder.itemView.setOnClickListener {
            val intent = Intent(context, view_profile::class.java).apply {
                // Pass the unique ID of the clicked user to the next activity
                putExtra("USER_ID", user.uid)
            }
            context.startActivity(intent)
        }
    }

}

