package com.rayaanraza.i230535

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.*

class SelectFriendActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val db = Firebase.database.reference
    private lateinit var friendsAdapter: FriendsAdapter
    private val friendsList = mutableListOf<User>()
    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_friend)

        postId = intent.getStringExtra("POST_ID")
        if (postId == null) {
            Toast.makeText(this, "Error: Post ID is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val rvFriends: RecyclerView = findViewById(R.id.rvFriends)
        rvFriends.layoutManager = LinearLayoutManager(this)
        friendsAdapter = FriendsAdapter(friendsList) { selectedUser ->
            // Return the selected user's ID to the calling activity
            val resultIntent = Intent()
            resultIntent.putExtra("SELECTED_USER_ID", selectedUser.uid)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        rvFriends.adapter = friendsAdapter

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        loadFriends()
    }

    private fun loadFriends() {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.child("users").child(currentUserUid).child("following").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                friendsList.clear()
                snapshot.children.forEach { child ->
                    val friendUid = child.key
                    if (friendUid != null) {
                        db.child("users").child(friendUid).addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userSnapshot: DataSnapshot) {
                                val user = userSnapshot.getValue(User::class.java)
                                if (user != null) {
                                    friendsList.add(user)
                                    friendsAdapter.notifyDataSetChanged()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SelectFriendActivity, "Failed to load friends.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

// Adapter for the friends list
class FriendsAdapter(
    private val friends: List<User>,
    private val onSendClick: (User) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.friend_avatar)
        val username: TextView = view.findViewById(R.id.friend_username)
        val sendButton: Button = view.findViewById(R.id.btnSendToFriend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_select, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = friends[position]
        holder.username.text = user.username
        Glide.with(holder.itemView.context)
            .load(user.profilePictureUrl)
            .circleCrop()
            .placeholder(R.drawable.default_avatar)
            .into(holder.avatar)

        holder.sendButton.setOnClickListener {
            onSendClick(user)
        }
    }

    override fun getItemCount() = friends.size
}

