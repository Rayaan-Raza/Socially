package com.group.i230535_i230048

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
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

class my_profile : AppCompatActivity() {

    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""

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

    fun loadBottomBarAvatar(navProfile: ImageView) {
        navProfile.loadUserAvatar(currentUserId, currentUserId, R.drawable.oval)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_profile)

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish(); return
        }

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

        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java)); finish()
        }

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
                    updateUserStatus(false)
                    val intent = Intent(this, login_sign::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateUserStatus(isOnline: Boolean) {
        // Note: This endpoint is not in the API spec, might need to be created
        val url = AppGlobals.BASE_URL + "user_update_status.php"
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { Log.d("my_profile", "User status updated") },
            { Log.e("my_profile", "Failed to update status: ${it.message}") }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid"] = currentUserId
                params["isOnline"] = if (isOnline) "1" else "0"
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun openPostDetail(post: Post) {
        val intent = Intent(this, GotoPostActivity::class.java).apply {
            putExtra("POST_ID", post.postId)
            putExtra("USER_ID", post.uid)
        }
        startActivity(intent)
    }

    private fun setupPostsGrid() {
        val spanCount = 3
        postsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        postsRecyclerView.setHasFixedSize(true)
        postsRecyclerView.addItemDecoration(GridSpacingDecoration(spanCount, dp(1), includeEdge = false))

        postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
            openPostDetail(clickedPost)
        }
        postsRecyclerView.adapter = postsAdapter
    }

    override fun onStart() {
        super.onStart()
        loadProfileFromDb()
        loadPostsFromDb()
        fetchProfileFromApi()
        fetchPostsFromApi()
    }

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

            profileImageView.loadUserAvatar(currentUserId, currentUserId, R.drawable.default_avatar)
        }
        cursor.close()
    }

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
                    imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                    likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                    commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                )
            )
        }
        cursor.close()
        postsAdapter.notifyDataSetChanged()
        postsCountText.text = postList.size.toString()
    }

    private fun fetchProfileFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("my_profile", "Offline, skipping profile fetch")
            return
        }

        Log.d("my_profile", "Fetching profile from API...")
        // API Spec: getUserProfile.php?uid=user_uid (or email=...)
        val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$currentUserId"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")

                        followersCountText.text = userObj.getInt("followersCount").toString()
                        followingCountText.text = userObj.getInt("followingCount").toString()
                        postsCountText.text = userObj.getInt("postsCount").toString()

                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.getString("uid"))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.getString("username"))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.getString("fullName"))
                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, userObj.optString("profilePictureUrl", ""))
                        cv.put(DB.User.COLUMN_EMAIL, userObj.getString("email"))
                        cv.put(DB.User.COLUMN_BIO, userObj.optString("bio", ""))
                        dbHelper.writableDatabase.insertWithOnConflict(
                            DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

                        loadProfileFromDb()
                    }
                } catch (e: Exception) { Log.e("my_profile", "Error parsing profile: ${e.message}") }
            },
            { error -> Log.w("my_profile", "Volley error fetching profile: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    private fun fetchPostsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d("my_profile", "Offline, skipping posts fetch")
            return
        }

        Log.d("my_profile", "Fetching posts from API...")
        // API Spec: profile_posts_get.php?targetUid=user_uid
        val url = AppGlobals.BASE_URL + "profile_posts_get.php?targetUid=$currentUserId"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        val db = dbHelper.writableDatabase
                        db.beginTransaction()
                        try {
                            db.delete(DB.Post.TABLE_NAME, "${DB.Post.COLUMN_UID} = ?", arrayOf(currentUserId))

                            for (i in 0 until dataArray.length()) {
                                val postObj = dataArray.getJSONObject(i)
                                val cv = ContentValues()
                                cv.put(DB.Post.COLUMN_POST_ID, postObj.getString("postId"))
                                cv.put(DB.Post.COLUMN_UID, postObj.getString("uid"))
                                cv.put(DB.Post.COLUMN_USERNAME, postObj.optString("username", ""))
                                cv.put(DB.Post.COLUMN_CAPTION, postObj.optString("caption", ""))
                                cv.put(DB.Post.COLUMN_IMAGE_URL, postObj.optString("imageUrl", ""))
                                cv.put(DB.Post.COLUMN_IMAGE_BASE64, postObj.optString("imageBase64", ""))
                                cv.put(DB.Post.COLUMN_CREATED_AT, postObj.getLong("createdAt"))
                                cv.put(DB.Post.COLUMN_LIKE_COUNT, postObj.optLong("likeCount", 0))
                                cv.put(DB.Post.COLUMN_COMMENT_COUNT, postObj.optLong("commentCount", 0))
                                db.insert(DB.Post.TABLE_NAME, null, cv)
                            }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
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
        loadProfileFromDb()
        loadPostsFromDb()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
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