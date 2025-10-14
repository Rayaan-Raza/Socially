package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class search_feed : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfilePostGridAdapter
    private lateinit var searchInput: EditText

    private val allPosts = mutableListOf<Post>()
    private val filteredPosts = mutableListOf<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_feed)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        setupViews()
        setupRecyclerView()
        setupNavigation()
        loadAllPosts()
    }

    private fun setupViews() {
        searchInput = findViewById(R.id.search)

        // Add text change listener for search
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterPosts(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.postsRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3) // 3 columns

        adapter = ProfilePostGridAdapter(filteredPosts) { clickedPost ->
            // Handle post click - you can open post detail or show full view
            Toast.makeText(this, "Post by ${clickedPost.username}", Toast.LENGTH_SHORT).show()
            // Navigate to post detail if you have that activity
            // val intent = Intent(this, PostDetailActivity::class.java)
            // intent.putExtra("postId", clickedPost.postId)
            // intent.putExtra("userId", clickedPost.uid)
            // startActivity(intent)
        }

        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.create_account).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.activity_page).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.scan).setOnClickListener {
            Toast.makeText(this, "Scan feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load all posts from all users
     */
    private fun loadAllPosts() {
        // Get all users first
        database.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userIds = mutableListOf<String>()

                for (child in snapshot.children) {
                    child.key?.let { userIds.add(it) }
                }

                if (userIds.isEmpty()) {
                    return
                }

                // Load posts from each user
                loadPostsFromUsers(userIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@search_feed, "Failed to load posts", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Load posts from multiple users
     */
    private fun loadPostsFromUsers(userIds: List<String>) {
        var loadedCount = 0
        allPosts.clear()

        for (userId in userIds) {
            database.getReference("posts").child(userId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (postSnapshot in snapshot.children) {
                            val post = postSnapshot.getValue(Post::class.java)
                            if (post != null) {
                                allPosts.add(post)
                            }
                        }

                        loadedCount++
                        if (loadedCount == userIds.size) {
                            // All posts loaded, sort and display
                            allPosts.sortByDescending { it.createdAt }
                            filteredPosts.clear()
                            filteredPosts.addAll(allPosts)
                            adapter.notifyDataSetChanged()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        loadedCount++
                    }
                })
        }
    }

    /**
     * Filter posts based on search query
     * Searches in username, caption, and other fields
     */
    private fun filterPosts(query: String) {
        filteredPosts.clear()

        if (query.isEmpty()) {
            // Show all posts if search is empty
            filteredPosts.addAll(allPosts)
        } else {
            // Filter posts by username or caption
            val lowerQuery = query.lowercase()
            filteredPosts.addAll(allPosts.filter { post ->
                post.username.lowercase().contains(lowerQuery) ||
                        post.caption.lowercase().contains(lowerQuery)
            })
        }

        adapter.notifyDataSetChanged()
    }
}