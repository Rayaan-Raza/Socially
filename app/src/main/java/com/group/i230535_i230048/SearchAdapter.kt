package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchAdapter(private val context: Context, private val userList: List<User>) :
    RecyclerView.Adapter<SearchAdapter.UserViewHolder>() {

    private val TAG = "SearchAdapter"

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Make these nullable to avoid crashes if IDs don't exist
        val avatar: ImageView? = try { itemView.findViewById(R.id.user_avatar) } catch (e: Exception) { null }
        val username: TextView? = try { itemView.findViewById(R.id.user_username) } catch (e: Exception) { null }
        val fullName: TextView? = try { itemView.findViewById(R.id.user_fullname) } catch (e: Exception) { null }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        Log.d(TAG, "onCreateViewHolder called")
        val view = LayoutInflater.from(context).inflate(R.layout.item_user_search, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount(): Int {
        Log.d(TAG, "getItemCount: ${userList.size}")
        return userList.size
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder position: $position")

        try {
            val user = userList[position]
            Log.d(TAG, "Binding user: ${user.username}")

            // Set username - with null check
            holder.username?.text = user.username.ifBlank { "user" }
            Log.d(TAG, "Username set")

            // Set full name - with null check
            val displayName = user.fullName.ifBlank {
                "${user.firstName} ${user.lastName}".trim().ifBlank { "User" }
            }
            holder.fullName?.text = displayName
            Log.d(TAG, "FullName set")

            // Set avatar - with null check and safe loading
            holder.avatar?.let { avatarView ->
                try {
                    // Just set default for now to test
                    avatarView.setImageResource(R.drawable.oval)
                    Log.d(TAG, "Avatar set to default")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting avatar: ${e.message}")
                }
            }

            // Click listener
            holder.itemView.setOnClickListener {
                try {
                    Log.d(TAG, "User clicked: ${user.uid}")
                    val intent = Intent(context, view_profile::class.java).apply {
                        putExtra("USER_ID", user.uid)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening profile: ${e.message}")
                }
            }

            Log.d(TAG, "Bind complete for position $position")

        } catch (e: Exception) {
            Log.e(TAG, "CRASH in onBindViewHolder at position $position: ${e.message}")
            e.printStackTrace()

            // Set safe defaults
            holder.username?.text = "user"
            holder.fullName?.text = "User"
            holder.avatar?.setImageResource(R.drawable.oval)
        }
    }
}