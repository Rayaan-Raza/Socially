package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.ContentValues // CHANGED
import android.content.Context // CHANGED
import android.content.Intent
import android.database.sqlite.SQLiteDatabase // CHANGED
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log // CHANGED
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
// REMOVED: Firebase Auth and Database
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import com.group.i230535_i230048.AppDbHelper // CHANGED
import com.group.i230535_i230048.DB // CHANGED
import org.json.JSONObject // CHANGED

class my_profile : AppCompatActivity() {

    // --- CHANGED: Replaced Firebase with local DB, Volley, and Session ---
    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""
    // ---

    // REMOVED: Firebase listeners and refs

    private lateinit var usernameText: TextView
    private lateinit var displayNameText: TextView
    private lateinit var bioLine1: TextView
    private lateinit var bioLine2: TextView
    private lateinit var postsCountText: TextView
    private lateinit var followersCountText: TextView
    private lateinit var followingCountText: TextView
    private lateinit var profileImageView: ImageView
    private lateinit var editProfileBtn: TextView

    private lateinit var postsRecyclerView: RecyclerView
    private lateinit var postsAdapter: ProfilePostGridAdapter
    private val postList = mutableListOf<Post>()


    // CHANGED: Migrated to read from local user avatar
    fun loadBottomBarAvatar(navProfile: ImageView) {
        // This function now uses the migrated AvatarUtils
        navProfile.loadUserAvatar(currentUserId, currentUserId, R.drawable.oval)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_profile)

        // --- CHANGED: Setup DB, Volley, and Session ---
        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        // ---

        val navProfile = findViewById<ImageView>(R.id.nav_profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupClickListeners()
        setupPostsGrid()
    }

    private fun initViews() {
        // (No changes here)
        usernameText = findViewById(R.id.username)
        displayNameText = findViewById(R.id.displayName)
        bioLine1 = findViewById(R.id.bioLine1)
        bioLine2 = findViewById(R.id.bioLine2)
        postsCountText = findViewById(R.id.postsCount)
        followersCountText = findViewById(R.id.followersCount)
        followingCountText = findViewById(R.id.followingCount)
        editProfileBtn = findViewById(R.id.editProfileBtn)
        profileImageView = findViewById(R.id.designHighlightIcon)
        postsRecyclerView = findViewById(R.id.posts_recycler_view)
    }

    private fun setupClickListeners() {
        findViewById<TextView>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, edit_profile::class.java))
        }

        findViewById<android.widget.LinearLayout>(R.id.followersStats).setOnClickListener {
            startActivity(
                Intent(this, FollowListActivity::class.java)
                    .putExtra("mode", "followers")
                    .putExtra("uid", currentUserId)
            )
        }

        findViewById<android.widget.LinearLayout>(R.id.followingStats).setOnClickListener {
            startActivity(
                Intent(this, FollowListActivity::class.java)
                    .putExtra("mode", "following")
                    .putExtra("uid", currentUserId)
            )
        }

        // (No changes to nav)
        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java)); finish()
        }
        // ... other nav clicks ...

        // --- CHANGED: Logout logic migrated ---
        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    // 1. Clear local session
                    getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()

                    // 2. Tell backend we are offline (optional but good)
                    updateUserStatus(false)

                    // 3. Go to login screen
                    val intent = Intent(this, login_sign::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // --- NEW: Helper function for logout ---
    private fun updateUserStatus(isOnline: Boolean) {
        val url = AppGlobals.BASE_URL + "update_status.php" // (from ApiService.kt)
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { Log.d("my_profile", "User status updated to offline") },
            { Log.e("my_profile", "Failed to update status: ${it.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["user_id"] = currentUserId
                params["is_online"] = isOnline.toString()
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun openPostDetail(post: Post) {
        // (No changes here, this is correct)
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
        }
        startActivity(intent)
    }

    private fun setupPostsGrid() {
        // (No changes here, this is correct)
        val spanCount = 3
        postsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        postsRecyclerView.setHasFixedSize(true)
        postsRecyclerView.addItemDecoration(GridSpacingDecoration(spanCount, dp(1), includeEdge = false))

        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            openPostDetail(clickedPost)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    // REMOVED: attachPostsListener, attachUserListener, attachCountsListeners

    // --- CHANGED: New "Offline-First" loading functions ---
    override fun onStart() {
        super.onStart()
        // 1. Load all data from local DB instantly
        loadProfileFromDb()
        loadPostsFromDb()

        // 2. Fetch fresh data from network
        fetchProfileFromApi()
        fetchPostsFromApi()
    }

    // --- NEW: Loads user info from local SQLite DB ---
    private fun loadProfileFromDb() {
        Log.d("my_profile", "Loading profile from DB...")
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.User.TABLE_NAME, null,
            "${DB.User.COLUMN_UID} = ?", arrayOf(currentUserId),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            val username = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_USERNAME))
            val fullName = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_FULL_NAME))
            val bio = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_BIO))

            usernameText.text = username
            displayNameText.text = fullName

            val bioLines = (bio ?: "").split("\n", limit = 2)
            bioLine1.text = bioLines.getOrNull(0) ?: ""
            bioLine2.text = bioLines.getOrNull(1) ?: ""

            // This now uses the local DB via AvatarUtils
            profileImageView.loadUserAvatar(currentUserId, currentUserId, R.drawable.default_avatar)

            // Note: Counts are not in the DB.User table
            // We'll update them when the API call finishes.
        }
        cursor.close()
    }

    // --- NEW: Loads posts from local SQLite DB ---
    private fun loadPostsFromDb() {
        Log.d("my_profile", "Loading posts from DB...")
        postList.clear()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.Post.TABLE_NAME, null,
            "${DB.Post.COLUMN_UID} = ?", arrayOf(currentUserId),
            null, null, DB.Post.COLUMN_CREATED_AT + " DESC"
        )

        while (cursor.moveToNext()) {
            postList.add(
                Post(
                    postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                    uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                    username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)),
                    caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)),
                    imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT))
                    // ... add other fields as needed
                )
            )
        }
        cursor.close()
        postsAdapter.notifyDataSetChanged()
        postsCountText.text = postList.size.toString()
    }

    // --- NEW: Fetches User Profile (and counts) from API ---
    private fun fetchProfileFromApi() {
        Log.d("my_profile", "Fetching profile from API...")
        val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$currentUserId" // [cite: 224-228]

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")

                        // 1. Update UI with counts from API
                        // The API doc provides these counts directly on the user object
                        followersCountText.text = userObj.getInt("followersCount").toString() // [cite: 138]
                        followingCountText.text = userObj.getInt("followingCount").toString() // [cite: 139]
                        postsCountText.text = userObj.getInt("postsCount").toString() // [cite: 140]

                        // 2. Save full user profile to local DB
                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.getString("uid"))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.getString("username"))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.getString("fullName"))
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, userObj.getString("profilePictureUrl"))
                        cv.put(DB.User.COLUMN_EMAIL, userObj.getString("email"))
                        cv.put(DB.User.COLUMN_BIO, userObj.getString("bio"))
                        // ... save other fields ...
                        dbHelper.writableDatabase.insertWithOnConflict(
                            DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        // 3. Reload from DB to show any other new info
                        loadProfileFromDb()
                    }
                } catch (e: Exception) { Log.e("my_profile", "Error parsing profile: ${e.message}") }
            },
            { error -> Log.w("my_profile", "Volley error fetching profile: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    // --- NEW: Fetches User's Posts from API ---
    private fun fetchPostsFromApi() {
        Log.d("my_profile", "Fetching posts from API...")
        // TODO: Dev A needs to create this API endpoint
        val url = AppGlobals.BASE_URL + "get_user_posts.php?uid=$currentUserId"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val listType = object : TypeToken<List<Post>>() {}.type
                        val newPosts: List<Post> = Gson().fromJson(dataArray.toString(), listType)

                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            // Clear old posts for this user
                            db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(currentUserId))
                            // Insert new ones
                            for (post in newPosts) {
                                val cv = ContentValues()
                                cv.put(DB.Post.COLUMN_POST_ID, post.postId)
                                cv.put(DB.Post.COLUMN_UID, post.uid)
                                cv.put(DB.Post.COLUMN_USERNAME, post.username)
                                cv.put(DB.Post.COLUMN_CAPTION, post.caption)
                                cv.put(DB.Post.COLUMN_IMAGE_URL, post.imageUrl)
                                cv.put(DB.Post.COLUMN_CREATED_AT, post.createdAt)
                                db.insert(DB.Post.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        // Reload posts from DB to refresh grid
                        loadPostsFromDb()
                    }
                } catch (e: Exception) { Log.e("my_profile", "Error parsing posts: ${e.message}") }
            },
            { error -> Log.w("my_profile", "Volley error fetching posts: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    override fun onResume() {
        super.onResume()
        // CHANGED: Load from DB on resume to see changes from edit_profile
        loadProfileFromDb()
        loadPostsFromDb()
    }

    override fun onStop() {
        super.onStop()
        // REMOVED: detachListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        // REMOVED: detachListeners()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    class GridSpacingDecoration(
        private val spanCount: Int, private val spacingPx: Int, private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect, view: android.view.View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % spanCount
            if (includeEdge) {
                outRect.left = spacingPx - column * spacingPx / spanCount
                outRect.right = (column + 1) * spacingPx / spanCount
                if (position < spanCount) outRect.top = spacingPx
                outRect.bottom = spacingPx
            } else {
                outRect.left = column * spacingPx / spanCount
                outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
                if (position >= spanCount) outRect.top = spacingPx
            }
        }
    }
}