package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rayaanraza.i230535.databinding.ItemStoryBubbleBinding

class home_page : AppCompatActivity() {

    // Cache usernames (uid -> username) to avoid repeated reads
    private val usernameCache = mutableMapOf<String, String>()

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
            onLikeToggle = { post, wantLike -> toggleLike(post, wantLike) },
            onCommentClick = { post -> showAddCommentDialog(post) }
        )
        rvFeed.adapter = postAdapter
        loadFeed()

        // --- Nav Buttons ---
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

        // People you follow (users/{uid}/following)
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

        // uids = me + following
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

            // Latest 2 comments + total count to control "View all"
            db.child("postComments").child(p.postId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val totalCount = s.childrenCount.toInt()
                        val allComments = s.children.mapNotNull { it.getValue(Comment::class.java) }
                            .sortedByDescending { it.createdAt }
                        val latestTwo = allComments.take(2)
                        postAdapter.setCommentPreview(p.postId, latestTwo)
                        postAdapter.setCommentTotal(p.postId, totalCount)
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    // NEW: optimistic like write + counter sync
    private fun toggleLike(post: Post, wantLike: Boolean) {
        val myUid = auth.currentUser?.uid ?: return
        val likeRef = db.child("postLikes").child(post.postId).child(myUid)

        if (wantLike) likeRef.setValue(true) else likeRef.removeValue()

        // Keep counters in sync in /posts
        val likeCountRef = db.child("posts").child(post.uid).child(post.postId).child("likeCount")
        likeCountRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(cur: MutableData): Transaction.Result {
                val c = (cur.getValue(Long::class.java) ?: 0L)
                cur.value = (c + if (wantLike) 1L else -1L).coerceAtLeast(0L)
                return Transaction.success(cur)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {}
        })

        // Optional: mirror in index if you read from there elsewhere
        db.child("postIndex").child(post.postId).child("likeCount")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(cur: MutableData): Transaction.Result {
                    val c = (cur.getValue(Long::class.java) ?: 0L)
                    cur.value = (c + if (wantLike) 1L else -1L).coerceAtLeast(0L)
                    return Transaction.success(cur)
                }
                override fun onComplete(e: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {}
            })
    }

    // ----- FEED ADAPTER -----
    inner class PostAdapter(
        private val onLikeToggle: (post: Post, liked: Boolean) -> Unit,
        private val onCommentClick: (post: Post) -> Unit
    ) : RecyclerView.Adapter<PostAdapter.PostVH>() {

        private val items = mutableListOf<Post>()
        private val likeState = mutableMapOf<String, Boolean>()            // postId -> I liked
        private val likeCounts = mutableMapOf<String, Int>()               // postId -> likes
        private val commentPreviews = mutableMapOf<String, List<Comment>>()// postId -> 2 latest
        private val commentTotals = mutableMapOf<String, Int>()            // postId -> total comments

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

        fun setCommentTotal(postId: String, total: Int) {
            commentTotals[postId] = total
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

        @SuppressLint("RecyclerView")
        override fun onBindViewHolder(h: PostVH, position: Int) {
            val item = items[position]

            // Username / caption with fallback + cache
            val shownName = if (item.username.isNotBlank()) item.username
            else usernameCache[item.uid] ?: "user"
            h.username.text = shownName
            h.tvCaption.text = "$shownName  ${item.caption}"

            if (item.username.isBlank()) {
                // Fetch once, update UI, and heal the DB post so next loads are fast
                db.child("users").child(item.uid).child("username")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            val name = (s.getValue(String::class.java) ?: "user").ifBlank { "user" }
                            usernameCache[item.uid] = name
                            val idx = items.indexOfFirst { it.postId == item.postId }
                            if (idx >= 0) notifyItemChanged(idx)
                            db.child("posts").child(item.uid).child(item.postId)
                                .child("username").setValue(name)
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }

            // Avatar (placeholder for now)
            h.avatar.setImageResource(R.drawable.oval)

            // Image: prefer URL (future-proof), else Base64 (RTDB-only)
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(h.postImage.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.person1)
                    .error(R.drawable.person1)
                    .into(h.postImage)
            } else if (item.imageBase64.isNotEmpty()) {
                val bytes = try {
                    android.util.Base64.decode(item.imageBase64, android.util.Base64.DEFAULT)
                } catch (_: Exception) { null }

                val bmp = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) {
                    h.postImage.setImageBitmap(bmp)
                } else {
                    h.postImage.setImageResource(R.drawable.person1)
                }
            } else {
                h.postImage.setImageResource(R.drawable.person1)
            }

            // Initial counts from the post object (fast first paint)
            val initialLikes = item.likeCount.toInt()
            h.tvLikes.text = if (initialLikes == 1) "1 like" else "$initialLikes likes"

            val initialComments = item.commentCount.toInt()
            h.tvViewAll.visibility = if (initialComments > 2) View.VISIBLE else View.GONE

            // Likes UI (will be overwritten by realtime listeners)
            val liked = likeState[item.postId] == true
            h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
            val liveCount = likeCounts[item.postId] ?: initialLikes
            h.tvLikes.text = if (liveCount == 1) "1 like" else "$liveCount likes"

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

            // Show "View all" only if total comments > 2
            val total = commentTotals[item.postId] ?: previews.size
            h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

            // Actions (optimistic like + open comment dialog)
            h.likeBtn.setOnClickListener {
                val currentlyLiked = likeState[item.postId] == true
                val wantLike = !currentlyLiked

                // Optimistic UI
                likeState[item.postId] = wantLike
                val base = likeCounts[item.postId] ?: item.likeCount.toInt()
                val newCount = (base + if (wantLike) 1 else -1).coerceAtLeast(0)
                likeCounts[item.postId] = newCount

                h.likeBtn.setImageResource(if (wantLike) R.drawable.liked else R.drawable.like)
                h.tvLikes.text = if (newCount == 1) "1 like" else "$newCount likes"

                // Server write
                onLikeToggle(item, wantLike)
            }
            h.commentBtn.setOnClickListener { onCommentClick(item) }
            h.tvViewAll.setOnClickListener { onCommentClick(item) }
        }

        override fun getItemCount() = items.size
    }

    // ---------- Comments ----------

    private fun showAddCommentDialog(post: Post) {
        val input = android.widget.EditText(this).apply {
            hint = "Write a comment…"
            setPadding(24, 24, 24, 24)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add comment")
            .setView(input)
            .setPositiveButton("Post") { d, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) addComment(post, text)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addComment(post: Post, text: String) {
        val myUid = auth.currentUser?.uid ?: return
        // fetch my username
        db.child("users").child(myUid).child("username")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val uname = (s.getValue(String::class.java) ?: "user").ifBlank { "user" }
                    val commentId = java.util.UUID.randomUUID().toString()
                    val comment = mapOf(
                        "commentId" to commentId,
                        "postId" to post.postId,
                        "uid" to myUid,
                        "username" to uname,
                        "text" to text,
                        "createdAt" to System.currentTimeMillis()
                    )

                    val updates = hashMapOf<String, Any>(
                        "/postComments/${post.postId}/$commentId" to comment
                    )
                    db.updateChildren(updates).addOnSuccessListener {
                        // bump counters
                        db.child("posts").child(post.uid).child(post.postId).child("commentCount")
                            .runTransaction(object : Transaction.Handler {
                                override fun doTransaction(cur: MutableData): Transaction.Result {
                                    cur.value = ((cur.getValue(Long::class.java) ?: 0L) + 1L)
                                    return Transaction.success(cur)
                                }
                                override fun onComplete(
                                    e: DatabaseError?, committed: Boolean, s2: DataSnapshot?
                                ) {}
                            })
                        db.child("postIndex").child(post.postId).child("commentCount")
                            .runTransaction(object : Transaction.Handler {
                                override fun doTransaction(cur: MutableData): Transaction.Result {
                                    cur.value = ((cur.getValue(Long::class.java) ?: 0L) + 1L)
                                    return Transaction.success(cur)
                                }
                                override fun onComplete(
                                    e: DatabaseError?, committed: Boolean, s2: DataSnapshot?
                                ) {}
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
