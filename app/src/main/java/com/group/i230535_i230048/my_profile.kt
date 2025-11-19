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

    companion object {
        private const val TAG = "my_profile"
    }

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
        Log.d(TAG, "loadBottomBarAvatar: Loading for uid=$currentUserId")
        try {
            navProfile.loadUserAvatar(currentUserId, currentUserId, R.drawable.oval)
        } catch (e: Exception) {
            Log.e(TAG, "loadBottomBarAvatar: Error", e)
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Starting my_profile")

        try {
            enableEdgeToEdge()
            setContentView(R.layout.activity_my_profile)

            dbHelper = AppDbHelper(this)
            queue = Volley.newRequestQueue(this)

            val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

            Log.d(TAG, "onCreate: currentUserId=$currentUserId")

            if (currentUserId.isEmpty()) {
                Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
                finish()
                return
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

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Exception", e)
            Toast.makeText(this, "Error initializing profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        Log.d(TAG, "initViews: Initializing views")
        try {
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
            Log.d(TAG, "initViews: All views initialized")
        } catch (e: Exception) {
            Log.e(TAG, "initViews: Error", e)
            throw e
        }
    }

    private fun setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Setting up click listeners")
        try {
            findViewById<TextView>(R.id.editProfileBtn).setOnClickListener {
                Log.d(TAG, "Edit profile clicked")
                startActivity(Intent(this, edit_profile::class.java))
            }

            findViewById<android.widget.LinearLayout>(R.id.followersStats).setOnClickListener {
                Log.d(TAG, "Followers stats clicked")
                startActivity(
                    Intent(this, FollowListActivity::class.java)
                        .putExtra("mode", "followers")
                        .putExtra("uid", currentUserId)
                )
            }

            findViewById<android.widget.LinearLayout>(R.id.followingStats).setOnClickListener {
                Log.d(TAG, "Following stats clicked")
                startActivity(
                    Intent(this, FollowListActivity::class.java)
                        .putExtra("mode", "following")
                        .putExtra("uid", currentUserId)
                )
            }

            findViewById<ImageView>(R.id.nav_home).setOnClickListener {
                Log.d(TAG, "Home nav clicked")
                startActivity(Intent(this, home_page::class.java))
                finish()
            }

            findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
                Log.d(TAG, "Menu icon clicked")
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
            Log.d(TAG, "setupClickListeners: All click listeners set")
        } catch (e: Exception) {
            Log.e(TAG, "setupClickListeners: Error", e)
        }
    }

    private fun updateUserStatus(isOnline: Boolean) {
        Log.d(TAG, "updateUserStatus: Setting online=$isOnline")
        val url = AppGlobals.BASE_URL + "user_update_status.php"
        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { Log.d(TAG, "User status updated") },
            { Log.e(TAG, "Failed to update status: ${it.message}") }
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
        Log.d(TAG, "openPostDetail: Opening postId=${post.postId}")
        try {
            val intent = Intent(this, GotoPostActivity::class.java).apply {
                putExtra("POST_ID", post.postId)
                putExtra("USER_ID", post.uid)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPostDetail: Error", e)
            Toast.makeText(this, "Error opening post", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPostsGrid() {
        Log.d(TAG, "setupPostsGrid: Setting up posts grid")
        try {
            val spanCount = 3
            postsRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
            postsRecyclerView.setHasFixedSize(true)
            postsRecyclerView.addItemDecoration(GridSpacingDecoration(spanCount, dp(1), includeEdge = false))

            postsAdapter = ProfilePostGridAdapter(postList) { clickedPost ->
                openPostDetail(clickedPost)
            }
            postsRecyclerView.adapter = postsAdapter
            Log.d(TAG, "setupPostsGrid: Posts grid setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "setupPostsGrid: Error", e)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Loading data")
        loadProfileFromDb()
        loadPostsFromDb()
        fetchProfileFromApi()
        fetchPostsFromApi()
    }

    private fun loadProfileFromDb() {
        Log.d(TAG, "loadProfileFromDb: Loading profile from DB for uid=$currentUserId")
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.User.TABLE_NAME, null,
                "${DB.User.COLUMN_UID} = ?", arrayOf(currentUserId),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                try {
                    val username = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_USERNAME))
                    val fullName = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_FULL_NAME))
                    val bio = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_BIO))

                    // Get both profilePictureUrl and photo (Base64)
                    val profilePicUrl = try {
                        cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PROFILE_PIC_URL))
                    } catch (e: Exception) {
                        Log.w(TAG, "loadProfileFromDb: No profilePictureUrl column", e)
                        null
                    }

                    val photoBase64 = try {
                        cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PHOTO))
                    } catch (e: Exception) {
                        Log.w(TAG, "loadProfileFromDb: No photo column", e)
                        null
                    }

                    Log.d(TAG, "loadProfileFromDb: username=$username, fullName=$fullName")
                    Log.d(TAG, "loadProfileFromDb: profilePicUrl=${profilePicUrl?.take(50)}")
                    Log.d(TAG, "loadProfileFromDb: photoBase64=${photoBase64?.take(50)}")

                    usernameText.text = username ?: "user"
                    displayNameText.text = fullName ?: username ?: "User"

                    val bioLines = (bio ?: "").split("\n", limit = 2)
                    bioLine1.text = bioLines.getOrNull(0) ?: ""
                    bioLine2.text = bioLines.getOrNull(1) ?: ""

                    // Load profile image - try both URL and Base64
                    loadProfileImage(profilePicUrl, photoBase64)

                    Log.d(TAG, "loadProfileFromDb: Profile loaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "loadProfileFromDb: Error parsing cursor data", e)
                }
            } else {
                Log.w(TAG, "loadProfileFromDb: No user found in DB for uid=$currentUserId")
                // Set defaults
                usernameText.text = "user"
                displayNameText.text = "User"
                bioLine1.text = ""
                bioLine2.text = ""
                profileImageView.setImageResource(R.drawable.default_avatar)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "loadProfileFromDb: Exception", e)
        }
    }

    private fun loadProfileImage(profilePicUrl: String?, photoBase64: String?) {
        Log.d(TAG, "loadProfileImage: profilePicUrl=${profilePicUrl != null}, photoBase64=${photoBase64 != null}")

        try {
            // Combine both into one string for loadAvatarFromString to handle
            val imageData = when {
                !profilePicUrl.isNullOrBlank() -> {
                    Log.d(TAG, "loadProfileImage: Using profilePicUrl")
                    profilePicUrl
                }
                !photoBase64.isNullOrBlank() -> {
                    Log.d(TAG, "loadProfileImage: Using photoBase64")
                    photoBase64
                }
                else -> {
                    Log.w(TAG, "loadProfileImage: No image data available")
                    null
                }
            }

            if (imageData != null) {
                profileImageView.loadAvatarFromString(imageData, R.drawable.default_avatar)
            } else {
                profileImageView.setImageResource(R.drawable.default_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadProfileImage: Error loading image", e)
            profileImageView.setImageResource(R.drawable.default_avatar)
        }
    }

    private fun loadPostsFromDb() {
        Log.d(TAG, "loadPostsFromDb: Loading posts from DB for uid=$currentUserId")
        try {
            postList.clear()
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.Post.TABLE_NAME, null,
                "${DB.Post.COLUMN_UID} = ?", arrayOf(currentUserId),
                null, null, "${DB.Post.COLUMN_CREATED_AT} DESC"
            )

            while (cursor.moveToNext()) {
                try {
                    postList.add(
                        Post(
                            postId = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_POST_ID)),
                            uid = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_UID)),
                            username = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_USERNAME)) ?: "",
                            caption = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CAPTION)) ?: "",
                            imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_URL)) ?: "",
                            imageBase64 = cursor.getString(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_IMAGE_BASE64)) ?: "",
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_CREATED_AT)),
                            likeCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_LIKE_COUNT)),
                            commentCount = cursor.getLong(cursor.getColumnIndexOrThrow(DB.Post.COLUMN_COMMENT_COUNT))
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "loadPostsFromDb: Error parsing post", e)
                }
            }
            cursor.close()

            postsAdapter.notifyDataSetChanged()
            postsCountText.text = postList.size.toString()

            Log.d(TAG, "loadPostsFromDb: Loaded ${postList.size} posts")
        } catch (e: Exception) {
            Log.e(TAG, "loadPostsFromDb: Exception", e)
        }
    }

    private fun fetchProfileFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d(TAG, "fetchProfileFromApi: Offline, skipping profile fetch")
            return
        }

        Log.d(TAG, "fetchProfileFromApi: Fetching profile from API for uid=$currentUserId")
        val url = "${AppGlobals.BASE_URL}getUserProfile.php?uid=$currentUserId"
        Log.d(TAG, "fetchProfileFromApi: URL=$url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchProfileFromApi: Response received")
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val userObj = json.getJSONObject("data")
                        Log.d(TAG, "fetchProfileFromApi: Parsing user data")

                        followersCountText.text = userObj.getInt("followersCount").toString()
                        followingCountText.text = userObj.getInt("followingCount").toString()
                        postsCountText.text = userObj.getInt("postsCount").toString()

                        val cv = ContentValues()
                        cv.put(DB.User.COLUMN_UID, userObj.getString("uid"))
                        cv.put(DB.User.COLUMN_USERNAME, userObj.getString("username"))
                        cv.put(DB.User.COLUMN_FULL_NAME, userObj.getString("fullName"))
                        cv.put(DB.User.COLUMN_EMAIL, userObj.getString("email"))
                        cv.put(DB.User.COLUMN_BIO, userObj.optString("bio", ""))

                        // Save both profilePictureUrl and photo
                        val profilePicUrl = userObj.optString("profilePictureUrl", "")
                        val photoBase64 = userObj.optString("photo", "")

                        Log.d(TAG, "fetchProfileFromApi: profilePictureUrl=${profilePicUrl.take(50)}")
                        Log.d(TAG, "fetchProfileFromApi: photo=${photoBase64.take(50)}")

                        cv.put(DB.User.COLUMN_PROFILE_PIC_URL, profilePicUrl)
                        cv.put(DB.User.COLUMN_PHOTO, photoBase64)

                        dbHelper.writableDatabase.insertWithOnConflict(
                            DB.User.TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE
                        )

                        loadProfileFromDb()
                        Log.d(TAG, "fetchProfileFromApi: Profile saved and reloaded")
                    } else {
                        Log.w(TAG, "fetchProfileFromApi: API returned success=false")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchProfileFromApi: Error parsing profile", e)
                }
            },
            { error ->
                Log.w(TAG, "fetchProfileFromApi: Volley error", error)
            }
        )
        queue.add(stringRequest)
    }

    private fun fetchPostsFromApi() {
        if (!isNetworkAvailable(this)) {
            Log.d(TAG, "fetchPostsFromApi: Offline, skipping posts fetch")
            return
        }

        Log.d(TAG, "fetchPostsFromApi: Fetching posts from API for uid=$currentUserId")
        val url = "${AppGlobals.BASE_URL}profile_posts_get.php?targetUid=$currentUserId"
        Log.d(TAG, "fetchPostsFromApi: URL=$url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                Log.d(TAG, "fetchPostsFromApi: Response received")
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        Log.d(TAG, "fetchPostsFromApi: Processing ${dataArray.length()} posts")

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
                        Log.d(TAG, "fetchPostsFromApi: Posts saved and reloaded")
                    } else {
                        Log.w(TAG, "fetchPostsFromApi: API returned success=false")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchPostsFromApi: Error parsing posts", e)
                }
            },
            { error ->
                Log.w(TAG, "fetchPostsFromApi: Volley error", error)
            }
        )
        queue.add(stringRequest)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Reloading data")
        loadProfileFromDb()
        loadPostsFromDb()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
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