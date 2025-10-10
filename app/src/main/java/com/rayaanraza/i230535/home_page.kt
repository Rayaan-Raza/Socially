package com.rayaanraza.i230535

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
    private lateinit var rvStories: RecyclerView
    private lateinit var storyAdapter: StoryAdapter
    private val storyList = mutableListOf<StoryBubble>()

    // Feed
    private lateinit var rvFeed: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // --- STORIES RecyclerView ---
        rvStories = findViewById(R.id.rvStories)
        rvStories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        storyAdapter = StoryAdapter(storyList)
        rvStories.adapter = storyAdapter
        loadStories()

        // --- FEED RecyclerView ---
        rvFeed = findViewById(R.id.rvFeed)
        rvFeed.layoutManager = LinearLayoutManager(this)
        postAdapter = PostAdapter(
            onLikeToggle = { postId, wantLike -> toggleLike(postId, wantLike) },
            onCommentClick = { postId ->
                // TODO: open comments
            }
        )
        rvFeed.adapter = postAdapter
        loadFeed()

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

    // ---------------- STORIES ----------------

    private fun loadStories() {
        val uid = auth.currentUser?.uid ?: return
        storyList.clear()

        // Your story first
        db.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            val myName = snapshot.child("username").getValue(String::class.java) ?: "You"
            val myPic = snapshot.child("profilePic").getValue(String::class.java)
            storyList.add(0, StoryBubble(uid, myName, myPic))
            storyAdapter.notifyDataSetChanged()
        }

        // People you follow (NOTE: reading from users/{uid}/following to match your current code)
        db.child("users").child(uid).child("following")
            .addListenerForSingleValueEvent(object : ValueEventListener {
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

    inner class StoryAdapter(private val items: List<StoryBubble>) :
        RecyclerView.Adapter<StoryAdapter.StoryVH>() {

        inner class StoryVH(val binding: ItemStoryBubbleBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryVH {
            val inflater = layoutInflater
            val binding = ItemStoryBubbleBinding.inflate(inflater, parent, false)
            return StoryVH(binding)
        }

        override fun onBindViewHolder(holder: StoryVH, position: Int) {
            val item = items[position]
            holder.binding.username.text = item.username
            if (!item.profileUrl.isNullOrEmpty()) {
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

    // ---------------- FEED (POSTS) ----------------

    private fun loadFeed() {
        val myUid = auth.currentUser?.uid ?: return

        // 1) collect uids = me + following (using users/{uid}/following to match your code)
        db.child("users").child(myUid).child("following")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val uids = mutableListOf<String>()
                    uids.add(myUid)
                    for (c in snapshot.children) uids.add(c.key ?: continue)
                    readPostsFor(uids)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun readPostsFor(uids: List<String>) {
        val all = mutableListOf<Post>()
        val remaining = uids.toMutableSet()
        if (uids.isEmpty()) {
            postAdapter.submitList(emptyList())
            return
        }

        for (u in uids) {
            db.child("posts").child(u)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        for (p in s.children) {
                            p.getValue(Post::class.java)?.let { all.add(it) }
                        }
                        remaining.remove(u)
                        if (remaining.isEmpty()) {
                            all.sortByDescending { it.createdAt }
                            postAdapter.submitList(all)
                            attachRealtime(all)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun attachRealtime(posts: List<Post>) {
        val myUid = auth.currentUser?.uid ?: return
        for (p in posts) {
            // Live like count + my like
            db.child("postLikes").child(p.postId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val count = s.childrenCount.toInt()
                        val iLike = s.child(myUid).getValue(Boolean::class.java) == true
                        postAdapter.setLikeCount(p.postId, count)
                        postAdapter.setLiked(p.postId, iLike)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

            // Latest 2 comments
            db.child("postComments").child(p.postId)
                .orderByChild("createdAt").limitToLast(2)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val list = s.children.mapNotNull { it.getValue(Comment::class.java) }
                            .sortedByDescending { it.createdAt }
                        postAdapter.setCommentPreview(p.postId, list)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    private fun toggleLike(postId: String, wantLike: Boolean) {
        val myUid = auth.currentUser?.uid ?: return
        val likeRef = db.child("postLikes").child(postId).child(myUid)
        if (wantLike) likeRef.setValue(true) else likeRef.removeValue()
    }

    // ----- FEED ADAPTER -----
    inner class PostAdapter(
        private val onLikeToggle: (postId: String, liked: Boolean) -> Unit,
        private val onCommentClick: (postId: String) -> Unit
    ) : RecyclerView.Adapter<PostAdapter.PostVH>() {

        private val items = mutableListOf<Post>()
        private val likeState = mutableMapOf<String, Boolean>()         // postId -> I liked
        private val likeCounts = mutableMapOf<String, Int>()             // postId -> likes
        private val commentPreviews = mutableMapOf<String, List<Comment>>() // postId -> 2 latest

        fun submitList(list: List<Post>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun setLikeCount(postId: String, count: Int) {
            likeCounts[postId] = count
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        fun setLiked(postId: String, liked: Boolean) {
            likeState[postId] = liked
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        fun setCommentPreview(postId: String, comments: List<Comment>) {
            commentPreviews[postId] = comments
            val idx = items.indexOfFirst { it.postId == postId }
            if (idx >= 0) notifyItemChanged(idx)
        }

        inner class PostVH(v: View) : RecyclerView.ViewHolder(v) {
            val avatar: ImageView = v.findViewById(R.id.imgAvatar)
            val username: TextView = v.findViewById(R.id.tvUsername)
            val postImage: ImageView = v.findViewById(R.id.imgPost)
            val likeBtn: ImageView = v.findViewById(R.id.btnLike)
            val tvLikes: TextView = v.findViewById(R.id.tvLikes)
            val tvCaption: TextView = v.findViewById(R.id.tvCaption)
            val tvC1: TextView = v.findViewById(R.id.tvComment1)
            val tvC2: TextView = v.findViewById(R.id.tvComment2)
            val tvViewAll: TextView = v.findViewById(R.id.tvViewAll)
            val commentBtn: ImageView = v.findViewById(R.id.btnComment)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false)
            return PostVH(v)
        }

        override fun onBindViewHolder(h: PostVH, position: Int) {
            val item = items[position]

            // Username / caption
            h.username.text = item.username.ifEmpty { "user" }
            h.tvCaption.text = "${item.username}  ${item.caption}"

            // Avatar (if you later store avatar, load it here. Use default now)
            h.avatar.setImageResource(R.drawable.oval)

            // Post image from Base64
            val bmp = decodeBase64(item.imageBase64)
            if (bmp != null) h.postImage.setImageBitmap(bmp) else h.postImage.setImageResource(R.drawable.person1)

            // Likes UI
            val liked = likeState[item.postId] == true
            h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
            val count = likeCounts[item.postId] ?: 0
            h.tvLikes.text = if (count == 1) "1 like" else "$count likes"

            // Comment previews
            val previews = commentPreviews[item.postId] ?: emptyList()
            if (previews.isNotEmpty()) {
                h.tvC1.visibility = View.VISIBLE
                h.tvC1.text = "${previews[0].username}  ${previews[0].text}"
            } else h.tvC1.visibility = View.GONE

            if (previews.size >= 2) {
                h.tvC2.visibility = View.VISIBLE
                h.tvC2.text = "${previews[1].username}  ${previews[1].text}"
            } else h.tvC2.visibility = View.GONE

            h.tvViewAll.visibility = if (previews.size > 2) View.VISIBLE else View.GONE

            // Actions
            h.likeBtn.setOnClickListener {
                val currentlyLiked = likeState[item.postId] == true
                onLikeToggle(item.postId, !currentlyLiked)
            }
            h.commentBtn.setOnClickListener { onCommentClick(item.postId) }
            h.tvViewAll.setOnClickListener { onCommentClick(item.postId) }
        }

        override fun getItemCount() = items.size

        private fun decodeBase64(b64: String?): Bitmap? {
            return try {
                if (b64.isNullOrEmpty()) null
                else {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Exception) { null }
        }
    }
}
