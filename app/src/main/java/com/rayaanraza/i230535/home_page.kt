package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
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

    // --- Caches & State ---
    private val usernameCache = mutableMapOf<String, String>()

    // Stories
    private lateinit var rvStories: RecyclerView
    private lateinit var storyAdapter: StoryAdapter
    private val storyList = mutableListOf<StoryBubble>()

    // Feed
    private lateinit var rvFeed: RecyclerView
    private lateinit var postAdapter: PostAdapter
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val currentPosts = mutableListOf<Post>()

    // --- Realtime listener bookkeeping (avoid leaks/duplicates) ---
    private val postChildListeners = mutableMapOf<String, ChildEventListener>()      // uid -> listener
    private val likeListeners = mutableMapOf<String, ValueEventListener>()           // postId -> listener
    private val commentPreviewListeners = mutableMapOf<String, ValueEventListener>() // postId -> listener
    private var watchingUids: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // STORIES
        rvStories = findViewById(R.id.rvStories)
        rvStories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        storyAdapter = StoryAdapter(storyList)
        rvStories.adapter = storyAdapter

        // FEED
        rvFeed = findViewById(R.id.rvFeed)
        rvFeed.layoutManager = LinearLayoutManager(this)
        postAdapter = PostAdapter(
            onLikeToggle = { post, wantLike -> toggleLike(post, wantLike) },
            onCommentClick = { post -> showAddCommentDialog(post) }
        )
        rvFeed.adapter = postAdapter

        // Nav
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

    override fun onStart() {
        super.onStart()
        // Refresh stories and feed whenever we (re)enter this screen.
        loadStories()
        loadFeed()
    }

    override fun onStop() {
        super.onStop()
        // Detach post listeners per UID
        postChildListeners.forEach { (uid, listener) ->
            db.child("posts").child(uid).removeEventListener(listener)
        }
        postChildListeners.clear()

        // Detach per-post likes listeners
        likeListeners.forEach { (postId, listener) ->
            db.child("postLikes").child(postId).removeEventListener(listener)
        }
        likeListeners.clear()

        // Detach per-post comment preview listeners
        commentPreviewListeners.forEach { (postId, listener) ->
            db.child("postComments").child(postId).removeEventListener(listener)
        }
        commentPreviewListeners.clear()
    }

    // ---------------- STORIES ----------------
    private fun loadStories() {
        val uid = auth.currentUser?.uid ?: return
        storyList.clear()

        db.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            val myName = snapshot.child("username").getValue(String::class.java) ?: "You"
            val myPic = snapshot.child("profilePic").getValue(String::class.java)
            storyList.add(0, StoryBubble(uid, myName, myPic))
            storyAdapter.notifyDataSetChanged()
        }

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

        // Clear current view to avoid stale duplication between sessions
        currentPosts.clear()
        postAdapter.submitList(emptyList())

        db.child("users").child(myUid).child("following")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val uids = mutableListOf<String>()
                    uids.add(myUid)
                    for (c in snapshot.children) c.key?.let { uids.add(it) }

                    // Initial load (posts -> comments) then show
                    readPostsFor(uids)

                    // Realtime: new/changed/removed posts
                    attachRealtimeFeed(uids)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun readPostsFor(uids: List<String>) {
        val collected = mutableListOf<Post>()
        val remaining = uids.toMutableSet()
        if (uids.isEmpty()) {
            currentPosts.clear()
            postAdapter.submitList(currentPosts)
            return
        }

        for (u in uids) {
            db.child("posts").child(u)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        for (p in s.children) {
                            p.getValue(Post::class.java)?.let { collected.add(it) }
                        }
                        remaining.remove(u)
                        if (remaining.isEmpty()) {
                            fetchInitialCommentsAndShow(collected)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    /** Fetch last 2 comments for every post, then submit the list once. */
    private fun fetchInitialCommentsAndShow(posts: MutableList<Post>) {
        if (posts.isEmpty()) {
            currentPosts.clear()
            postAdapter.submitList(emptyList())
            return
        }

        val previewsByPost = mutableMapOf<String, List<Comment>>()
        val totalsByPost = mutableMapOf<String, Int>()
        var pending = posts.size

        posts.forEach { post ->
            db.child("postComments").child(post.postId)
                .orderByChild("createdAt")
                .limitToLast(2)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val latestTwo = snapshot.children
                            .mapNotNull { it.getValue(Comment::class.java) }
                            .sortedByDescending { it.createdAt }

                        previewsByPost[post.postId] = latestTwo

                        val totalFromPost = post.commentCount.toInt()
                        totalsByPost[post.postId] = if (totalFromPost > 0) totalFromPost else latestTwo.size

                        pending--
                        if (pending == 0) {
                            posts.sortByDescending { it.createdAt }
                            currentPosts.clear()
                            currentPosts.addAll(posts)
                            postAdapter.submitList(currentPosts.toList())

                            previewsByPost.forEach { (pid, list) -> postAdapter.setCommentPreview(pid, list) }
                            totalsByPost.forEach { (pid, total) -> postAdapter.setCommentTotal(pid, total) }

                            currentPosts.forEach { attachRealtimeFor(it) }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        pending--
                        if (pending == 0) {
                            posts.sortByDescending { it.createdAt }
                            currentPosts.clear()
                            currentPosts.addAll(posts)
                            postAdapter.submitList(currentPosts.toList())
                            currentPosts.forEach { attachRealtimeFor(it) }
                        }
                    }
                })
        }
    }

    // Realtime: listen for new/changed/removed posts for each uid — with dedupe + bookkeeping
    private fun attachRealtimeFeed(uids: List<String>) {
        watchingUids = uids

        for (u in uids) {
            if (postChildListeners.containsKey(u)) continue // already attached for this uid

            val listener = object : ChildEventListener {
                override fun onChildAdded(s: DataSnapshot, previousChildName: String?) {
                    val p = s.getValue(Post::class.java) ?: return
                    upsertPost(p)
                    attachRealtimeFor(p) // likes + comment previews
                }
                override fun onChildChanged(s: DataSnapshot, previousChildName: String?) {
                    val p = s.getValue(Post::class.java) ?: return
                    upsertPost(p)
                }
                override fun onChildRemoved(s: DataSnapshot) {
                    val p = s.getValue(Post::class.java) ?: return
                    val idx = currentPosts.indexOfFirst { it.postId == p.postId }
                    if (idx >= 0) {
                        // Also detach per-post listeners we were tracking
                        detachRealtimeFor(p.postId)
                        currentPosts.removeAt(idx)
                        postAdapter.submitList(currentPosts.toList())
                    }
                }
                override fun onChildMoved(s: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            }

            db.child("posts").child(u).addChildEventListener(listener)
            postChildListeners[u] = listener
        }
    }

    private fun upsertPost(p: Post) {
        val idx = currentPosts.indexOfFirst { it.postId == p.postId }
        if (idx >= 0) currentPosts[idx] = p else currentPosts.add(p)
        currentPosts.sortByDescending { it.createdAt }
        postAdapter.submitList(currentPosts.toList())
    }

    // Realtime per-post listeners for likes + last 2 comments. We track and remove in onStop().
    private fun attachRealtimeFor(p: Post) {
        val myUid = auth.currentUser?.uid ?: return

        // Likes (attach once per post)
        if (!likeListeners.containsKey(p.postId)) {
            val likeListener = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val count = s.childrenCount.toInt()
                    val iLike = s.child(myUid).getValue(Boolean::class.java) == true
                    postAdapter.setLikeCount(p.postId, count)
                    postAdapter.setLiked(p.postId, iLike)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db.child("postLikes").child(p.postId).addValueEventListener(likeListener)
            likeListeners[p.postId] = likeListener
        }

        // Latest 2 comments (preview) + total (prefer posts/.../commentCount if kept fresh)
        if (!commentPreviewListeners.containsKey(p.postId)) {
            val commentsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val latestTwo = snapshot.children
                        .mapNotNull { it.getValue(Comment::class.java) }
                        .sortedByDescending { it.createdAt }

                    val totalFromPost =
                        currentPosts.find { it.postId == p.postId }?.commentCount?.toInt() ?: latestTwo.size
                    postAdapter.setCommentPreview(p.postId, latestTwo)
                    postAdapter.setCommentTotal(p.postId, totalFromPost)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            db.child("postComments").child(p.postId)
                .orderByChild("createdAt")
                .limitToLast(2)
                .addValueEventListener(commentsListener)
            commentPreviewListeners[p.postId] = commentsListener
        }
    }

    private fun detachRealtimeFor(postId: String) {
        likeListeners.remove(postId)?.let {
            db.child("postLikes").child(postId).removeEventListener(it)
        }
        commentPreviewListeners.remove(postId)?.let {
            db.child("postComments").child(postId).removeEventListener(it)
        }
    }

    // Optimistic like write + counter sync
    private fun toggleLike(post: Post, wantLike: Boolean) {
        val myUid = auth.currentUser?.uid ?: return
        val likeRef = db.child("postLikes").child(post.postId).child(myUid)

        if (wantLike) likeRef.setValue(true) else likeRef.removeValue()

        val likeCountRef = db.child("posts").child(post.uid).child(post.postId).child("likeCount")
        likeCountRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(cur: MutableData): Transaction.Result {
                val c = (cur.getValue(Long::class.java) ?: 0L)
                cur.value = (c + if (wantLike) 1L else -1L).coerceAtLeast(0L)
                return Transaction.success(cur)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {}
        })

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
                                override fun onComplete(e: DatabaseError?, committed: Boolean, s2: DataSnapshot?) {}
                            })
                        db.child("postIndex").child(post.postId).child("commentCount")
                            .runTransaction(object : Transaction.Handler {
                                override fun doTransaction(cur: MutableData): Transaction.Result {
                                    cur.value = ((cur.getValue(Long::class.java) ?: 0L) + 1L)
                                    return Transaction.success(cur)
                                }
                                override fun onComplete(e: DatabaseError?, committed: Boolean, s2: DataSnapshot?) {}
                            })
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // --- Utils ---
    private fun decodeBase64ToBitmap(raw: String?): Bitmap? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    // ----- FEED ADAPTER (kept as your inner adapter so item_post works as-is) -----
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

            // Avatar
            h.avatar.setImageResource(R.drawable.oval)

            // Image: prefer URL, else Base64 (strip prefix)
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(h.postImage.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.person1)
                    .error(R.drawable.person1)
                    .into(h.postImage)
            } else if (item.imageBase64.isNotEmpty()) {
                val bmp = decodeBase64ToBitmap(item.imageBase64)
                if (bmp != null) h.postImage.setImageBitmap(bmp) else h.postImage.setImageResource(R.drawable.person1)
            } else {
                h.postImage.setImageResource(R.drawable.person1)
            }

            // Likes (realtime may overwrite)
            val initialLikes = item.likeCount.toInt()
            val liked = likeState[item.postId] == true
            h.likeBtn.setImageResource(if (liked) R.drawable.liked else R.drawable.like)
            val liveCount = likeCounts[item.postId] ?: initialLikes
            h.tvLikes.text = if (liveCount == 1) "1 like" else "$liveCount likes"

            // Comment previews (up to 2)
            val previews = commentPreviews[item.postId] ?: emptyList()
            if (previews.isNotEmpty()) {
                h.tvC1.visibility = View.VISIBLE
                h.tvC1.text = "${previews[0].username}: ${previews[0].text}"
            } else {
                h.tvC1.visibility = View.GONE
                h.tvC1.text = ""
            }
            if (previews.size >= 2) {
                h.tvC2.visibility = View.VISIBLE
                h.tvC2.text = "${previews[1].username}: ${previews[1].text}"
            } else {
                h.tvC2.visibility = View.GONE
                h.tvC2.text = ""
            }

            // "View all"
            val total = commentTotals[item.postId] ?: item.commentCount.toInt()
            h.tvViewAll.visibility = if (total > 2) View.VISIBLE else View.GONE

            // Actions
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

                onLikeToggle(item, wantLike)
            }
            h.commentBtn.setOnClickListener { onCommentClick(item) }
            h.tvViewAll.setOnClickListener { onCommentClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
