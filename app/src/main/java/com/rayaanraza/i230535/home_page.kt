package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.rayaanraza.i230535.databinding.ItemStoryBubbleBinding


class home_page : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var storyAdapter: StoryAdapter
    private val storyList = mutableListOf<StoryBubble>()
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // --- RecyclerView Setup ---
        recyclerView = findViewById(R.id.rvStories)
        recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        storyAdapter = StoryAdapter(storyList)
        recyclerView.adapter = storyAdapter

        loadStories()

        // --- Existing Nav Buttons ---
        findViewById<ImageView>(R.id.heart).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
        }
        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
        }
        findViewById<ImageView>(R.id.dms).setOnClickListener {
            startActivity(Intent(this, dms::class.java))
        }
        findViewById<ImageView>(R.id.camera).setOnClickListener {
            startActivity(Intent(this, camera_activiy::class.java))
        }
        findViewById<ImageView>(R.id.post).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
        }
        findViewById<ImageView>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
        }
    }

    private fun loadStories() {
        val uid = auth.currentUser?.uid ?: return
        storyList.clear()

        // 1️⃣ Add "Your Story" at start
        db.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            val myName = snapshot.child("username").getValue(String::class.java) ?: "You"
            val myPic = snapshot.child("profilePic").getValue(String::class.java)
            storyList.add(0, StoryBubble(uid, myName, myPic))
            storyAdapter.notifyDataSetChanged()
        }

        // 2️⃣ Load following users
        db.child("users").child(uid).child("following").addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val friendUid = child.key ?: continue
                    db.child("users").child(friendUid).get().addOnSuccessListener { userSnap ->
                        val uname = userSnap.child("username").getValue(String::class.java) ?: "User"
                        val pfp = userSnap.child("profilePic").getValue(String::class.java)
                        storyList.add(StoryBubble(friendUid, uname, pfp))
                        storyAdapter.notifyDataSetChanged()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- RecyclerView Adapter for stories ---
    inner class StoryAdapter(private val items: List<StoryBubble>) :
        RecyclerView.Adapter<StoryAdapter.StoryVH>() {

        inner class StoryVH(val binding: ItemStoryBubbleBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
            val inflater = layoutInflater
            val binding = ItemStoryBubbleBinding.inflate(inflater, parent, false)
            return StoryVH(binding)
        }


        override fun onBindViewHolder(holder: StoryVH, position: Int) {
            val item = items[position]

            holder.binding.username.text = item.username

            if (item.profileUrl != null && item.profileUrl!!.isNotEmpty()) {
                Glide.with(this@home_page)
                    .load(item.profileUrl)
                    .placeholder(R.drawable.person1)
                    .error(R.drawable.person1)
                    .into(holder.binding.pfp)
            } else {
                holder.binding.pfp.setImageResource(R.drawable.person1)
            }

            holder.binding.root.setOnClickListener {
                val intent = Intent(this@home_page, camera_story::class.java)
                intent.putExtra("uid", item.uid)
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
    }
}
