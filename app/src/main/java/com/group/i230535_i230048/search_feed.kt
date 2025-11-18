package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class search_feed : AppCompatActivity() {

    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfilePostGridAdapter
    private lateinit var searchInput: EditText

    private val allPosts = mutableListOf<Post>()
    private val filteredPosts = mutableListOf<Post>()

    fun loadBottomBarAvatar(navProfile: ImageView) {
        try {
            navProfile.loadUserAvatar(currentUserId, currentUserId, R.drawable.oval)
        } catch (e: Exception) {
            Log.e("search_feed", "Error loading avatar: ${e.message}")
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_feed)

        queue = Volley.newRequestQueue(this)
        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Session expired. Please log in.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, login_sign::class.java))
            finish()
            return
        }

        val navProfile = findViewById<ImageView>(R.id.profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
        setupRecyclerView()
        setupNavigation()
        fetchExploreFeedFromApi()
    }

    private fun setupViews() {
        try {
            searchInput = findViewById(R.id.search)
            searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    try {
                        filterPosts(s?.toString() ?: "")
                    } catch (e: Exception) {
                        Log.e("search_feed", "Error in text change: ${e.message}")
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        } catch (e: Exception) {
            Log.e("search_feed", "Error setting up views: ${e.message}")
        }
    }

    private fun openPostDetail(post: Post) {
        try {
            val intent = Intent(this, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", post.postId ?: "")
                putExtra("USER_ID", post.uid ?: "")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("search_feed", "Error opening post: ${e.message}")
            Toast.makeText(this, "Unable to open post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        try {
            recyclerView = findViewById(R.id.postsRecyclerView)
            recyclerView.layoutManager = GridLayoutManager(this, 3)

            adapter = ProfilePostGridAdapter(filteredPosts) { clickedPost ->
                try {
                    openPostDetail(clickedPost)
                } catch (e: Exception) {
                    Log.e("search_feed", "Error in post click: ${e.message}")
                }
            }
            recyclerView.adapter = adapter
        } catch (e: Exception) {
            Log.e("search_feed", "Error setting up RecyclerView: ${e.message}")
            Toast.makeText(this, "Error loading posts view", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        try {
            // Home button
            findViewById<ImageView>(R.id.home)?.setOnClickListener {
                try {
                    startActivity(Intent(this, home_page::class.java))
                    finish()
                } catch (e: Exception) {
                    Log.e("search_feed", "Error navigating to home: ${e.message}")
                }
            }

            // Search input - opens specific search
            findViewById<EditText>(R.id.search)?.setOnClickListener {
                try {
                    startActivity(Intent(this, specific_search::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error opening specific search: ${e.message}")
                }
            }

            // Activity/Heart button
            findViewById<ImageView>(R.id.heart)?.setOnClickListener {
                try {
                    startActivity(Intent(this, following_page::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error navigating to following: ${e.message}")
                }
            }

            // DMs button
            findViewById<ImageView>(R.id.dms)?.setOnClickListener {
                try {
                    startActivity(Intent(this, dms::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error opening DMs: ${e.message}")
                }
            }

            // Camera button
            findViewById<ImageView>(R.id.camera)?.setOnClickListener {
                try {
                    startActivity(Intent(this, camera_activiy::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error opening camera: ${e.message}")
                }
            }

            // Post button
            findViewById<ImageView>(R.id.post)?.setOnClickListener {
                try {
                    startActivity(Intent(this, posting::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error opening posting: ${e.message}")
                }
            }

            // Profile button
            findViewById<ImageView>(R.id.profile)?.setOnClickListener {
                try {
                    startActivity(Intent(this, my_profile::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error opening profile: ${e.message}")
                }
            }

            // Support old navigation IDs (from Firebase version)
            findViewById<ImageView>(R.id.activity_page)?.setOnClickListener {
                try {
                    startActivity(Intent(this, following_page::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error with activity_page: ${e.message}")
                }
            }

            findViewById<ImageView>(R.id.create_account)?.setOnClickListener {
                try {
                    startActivity(Intent(this, posting::class.java))
                } catch (e: Exception) {
                    Log.e("search_feed", "Error with create_account: ${e.message}")
                }
            }

            findViewById<ImageView>(R.id.scan)?.setOnClickListener {
                Toast.makeText(this, "Scan feature coming soon", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("search_feed", "Error setting up navigation: ${e.message}")
        }
    }

    // API REFERENCE: Section 3.5 - posts_search.php
    // GET posts_search.php?q=<query>&limit=<limit>&offset=<offset>
    // Returns: Array of Post objects with username included
    private fun fetchExploreFeedFromApi() {
        Log.d("search_feed", "Fetching explore feed from API...")

        try {
            // Using posts_search.php with empty query for explore/discover feed
            val url = AppGlobals.BASE_URL + "posts_search.php?q=&limit=100&offset=0"

            val stringRequest = StringRequest(Request.Method.GET, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.optBoolean("success", false)) {
                            val dataArray = json.optJSONArray("data")

                            if (dataArray != null && dataArray.length() > 0) {
                                val listType = object : TypeToken<List<Post>>() {}.type
                                val posts: List<Post> = Gson().fromJson(dataArray.toString(), listType)

                                // Sanitize posts - ensure no null fields
                                val sanitizedPosts = posts.mapNotNull { post ->
                                    try {
                                        post.copy(
                                            postId = post.postId ?: "",
                                            uid = post.uid ?: "",
                                            username = post.username?.ifBlank { "user" } ?: "user",
                                            caption = post.caption ?: "",
                                            imageUrl = post.imageUrl ?: "",
                                            imageBase64 = post.imageBase64 ?: ""
                                        )
                                    } catch (e: Exception) {
                                        Log.e("search_feed", "Error sanitizing post: ${e.message}")
                                        null
                                    }
                                }

                                allPosts.clear()
                                allPosts.addAll(sanitizedPosts)
                                filterPosts("") // Load all posts into the filter

                                Log.d("search_feed", "Loaded ${sanitizedPosts.size} explore posts")
                            } else {
                                Log.w("search_feed", "No posts in data array")
                                showEmptyState()
                            }
                        } else {
                            val errorMsg = json.optString("message", "Unknown error")
                            Log.w("search_feed", "API error: $errorMsg")
                            showEmptyState()
                        }
                    } catch (e: Exception) {
                        Log.e("search_feed", "Error parsing feed: ${e.message}")
                        showEmptyState()
                    }
                },
                { error ->
                    Log.e("search_feed", "Volley error fetching feed: ${error.message}")
                    showEmptyState()
                }
            )
            queue.add(stringRequest)
        } catch (e: Exception) {
            Log.e("search_feed", "Error initiating API call: ${e.message}")
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        try {
            runOnUiThread {
                allPosts.clear()
                filteredPosts.clear()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "No posts to display", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("search_feed", "Error showing empty state: ${e.message}")
        }
    }

    private fun filterPosts(query: String) {
        try {
            filteredPosts.clear()

            if (query.isEmpty()) {
                filteredPosts.addAll(allPosts)
            } else {
                val lowerQuery = query.lowercase().trim()

                // Safe filtering with null checks
                val filtered = allPosts.filter { post ->
                    try {
                        val usernameMatch = post.username?.lowercase()?.contains(lowerQuery) == true
                        val captionMatch = post.caption?.lowercase()?.contains(lowerQuery) == true
                        usernameMatch || captionMatch
                    } catch (e: Exception) {
                        Log.e("search_feed", "Error filtering post: ${e.message}")
                        false
                    }
                }

                filteredPosts.addAll(filtered)
            }

            adapter.notifyDataSetChanged()
            Log.d("search_feed", "Filtered to ${filteredPosts.size} posts")
        } catch (e: Exception) {
            Log.e("search_feed", "Error filtering posts: ${e.message}")
            // On error, show all posts
            filteredPosts.clear()
            filteredPosts.addAll(allPosts)
            adapter.notifyDataSetChanged()
        }
    }
}