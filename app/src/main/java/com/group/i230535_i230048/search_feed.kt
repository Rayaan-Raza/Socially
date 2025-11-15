package com.group.i230535_i230048

import android.content.Context // CHANGED
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log // CHANGED
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
import com.bumptech.glide.Glide
// REMOVED: Firebase imports
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import org.json.JSONObject // CHANGED

class search_feed : AppCompatActivity() {

    // --- CHANGED: Removed Firebase, added Volley and Session ---
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""
    // ---

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProfilePostGridAdapter // Assuming this adapter is available
    private lateinit var searchInput: EditText

    private val allPosts = mutableListOf<Post>()
    private val filteredPosts = mutableListOf<Post>()

    // --- CHANGED: Migrated to use AvatarUtils ---
    fun loadBottomBarAvatar(navProfile: ImageView) {
        navProfile.loadUserAvatar(currentUserId, currentUserId, R.drawable.oval)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_feed)

        // --- CHANGED: Setup Volley and Session ---
        queue = Volley.newRequestQueue(this)
        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        // ---

        val navProfile = findViewById<ImageView>(R.id.profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // REMOVED: Firebase init

        setupViews()
        setupRecyclerView()
        setupNavigation()

        // --- CHANGED: Call new API function ---
        fetchExploreFeedFromApi()
    }

    private fun setupViews() {
        searchInput = findViewById(R.id.search)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterPosts(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun openPostDetail(post: Post) {
        // (No changes needed)
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
        }
        startActivity(intent)
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.postsRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        // Assuming ProfilePostGridAdapter exists and works
        adapter = ProfilePostGridAdapter(filteredPosts) { clickedPost ->
            openPostDetail(clickedPost)
        }
        recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        // (No changes needed, this logic is correct)
        findViewById<ImageView>(R.id.home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java)); finish()
        }
        findViewById<EditText>(R.id.search).setOnClickListener {
            startActivity(Intent(this, specific_search::class.java)) // Don't finish() here
        }
        // ... other nav clicks
    }

    // --- CHANGED: Replaced loadAllPosts and loadPostsFromUsers ---
    private fun fetchExploreFeedFromApi() {
        // TODO: Dev A needs to create this API endpoint
        val url = AppGlobals.BASE_URL + "get_explore_feed.php"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        val listType = object : TypeToken<List<Post>>() {}.type
                        val posts: List<Post> = Gson().fromJson(dataArray.toString(), listType)

                        allPosts.clear()
                        allPosts.addAll(posts)
                        filterPosts("") // Load all posts into the filter

                    } else {
                        Toast.makeText(this, "Failed to load feed: ${json.getString("message")}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("search_feed", "Error parsing feed: ${e.message}")
                }
            },
            { error ->
                Log.e("search_feed", "Volley error fetching feed: ${error.message}")
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(stringRequest)
    }

    // REMOVED: loadAllPosts(), loadPostsFromUsers()

    private fun filterPosts(query: String) {
        // (No changes needed, this logic is fine)
        filteredPosts.clear()
        if (query.isEmpty()) {
            filteredPosts.addAll(allPosts)
        } else {
            val lowerQuery = query.lowercase()
            filteredPosts.addAll(allPosts.filter { post ->
                post.username.lowercase().contains(lowerQuery) ||
                        post.caption.lowercase().contains(lowerQuery)
            })
        }
        adapter.notifyDataSetChanged()
    }
}