package com.group.i230535_i230048

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class camera_story : AppCompatActivity() {

    private lateinit var queue: RequestQueue
    private var currentUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_story)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUid.isEmpty()) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val img = findViewById<ImageView>(R.id.storyImage)
        val close = findViewById<ImageView>(R.id.closeBtn)
        close.setOnClickListener { finish() }

        // Get target user ID from intent, default to current user (for "Your Story")
        val targetUid = intent.getStringExtra("uid") ?: currentUid

        // API REFERENCE: Section 4.3 - stories_get_user.php
        // GET stories_get_user.php?user_uid=<uid>
        // Returns non-expired stories of this user
        fetchUserStoriesFromApi(targetUid, img)
    }

    private fun fetchUserStoriesFromApi(targetUid: String, imageView: ImageView) {
        Log.d("camera_story", "Fetching stories for user: $targetUid")

        val url = AppGlobals.BASE_URL + "stories_get_user.php?user_uid=$targetUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    Log.d("camera_story", "Raw API response: $response")
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("stories")
                        Log.d("camera_story", "Stories array: $dataArray")

                        val listType = object : TypeToken<List<Story_data>>() {}.type
                        val stories: List<Story_data> = Gson().fromJson(dataArray.toString(), listType)

                        Log.d("camera_story", "Parsed ${stories.size} stories")

                        if (stories.isEmpty()) {
                            Toast.makeText(this, "No active stories!", Toast.LENGTH_SHORT).show()
                            finish()
                            return@StringRequest
                        }

                        // Find latest non-expired story
                        val now = System.currentTimeMillis()
                        val latestStory = stories
                            .filter { it.expiresAt > now }
                            .maxByOrNull { it.createdAt }

                        if (latestStory == null) {
                            Toast.makeText(this, "No active stories!", Toast.LENGTH_SHORT).show()
                            finish()
                            return@StringRequest
                        }

                        Log.d("camera_story", "Latest story - storyId: ${latestStory.storyId}, mediaUrl: ${latestStory.mediaUrl.take(50)}, mediaBase64 length: ${latestStory.mediaBase64.length}")

                        // Load story media
                        loadStoryMedia(latestStory, imageView)

                    } else {
                        val errorMsg = json.optString("message", "No active stories")
                        Log.w("camera_story", "API error: $errorMsg")
                        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("camera_story", "Error parsing stories: ${e.message}")
                    Log.e("camera_story", "Response was: $response")
                    Log.e("camera_story", "Stack trace: ${e.stackTraceToString()}")
                    Toast.makeText(this, "Error loading story: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            { error ->
                Log.e("camera_story", "Volley error: ${error.message}")
                error.networkResponse?.let {
                    Log.e("camera_story", "Network response code: ${it.statusCode}")
                    Log.e("camera_story", "Network response data: ${String(it.data)}")
                }
                Toast.makeText(this, "Failed to load story: ${error.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        )
        queue.add(stringRequest)
    }

    private fun loadStoryMedia(story: Story_data, imageView: ImageView) {
        try {
            Log.d("camera_story", "Loading story media...")
            Log.d("camera_story", "mediaUrl: ${story.mediaUrl.take(100)}")
            Log.d("camera_story", "mediaBase64 length: ${story.mediaBase64.length}")

            // Priority 1: Check if mediaBase64 has data (this is what PHP returns)
            if (story.mediaBase64.isNotEmpty()) {
                try {
                    Log.d("camera_story", "Attempting to load from mediaBase64 field")
                    val imageBytes = Base64.decode(story.mediaBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        Log.d("camera_story", "Successfully loaded story from mediaBase64")
                        return
                    } else {
                        Log.w("camera_story", "Bitmap decode returned null from mediaBase64")
                    }
                } catch (e: Exception) {
                    Log.e("camera_story", "Failed to load from mediaBase64: ${e.message}")
                }
            }

            // Priority 2: Check if it's a URL
            if (story.mediaUrl.isNotEmpty() &&
                (story.mediaUrl.startsWith("http://") || story.mediaUrl.startsWith("https://"))) {
                Log.d("camera_story", "Attempting to load from URL: ${story.mediaUrl}")
                Glide.with(this)
                    .load(story.mediaUrl)
                    .placeholder(R.drawable.person1)
                    .error(R.drawable.person1)
                    .into(imageView)
                return
            }

            // Priority 3: Check if mediaUrl contains base64 data (fallback)
            if (story.mediaUrl.isNotEmpty()) {
                try {
                    Log.d("camera_story", "Attempting to load mediaUrl as base64")
                    val imageBytes = Base64.decode(story.mediaUrl, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        Log.d("camera_story", "Successfully loaded story from mediaUrl as base64")
                        return
                    }
                } catch (e: Exception) {
                    Log.e("camera_story", "Failed to load mediaUrl as base64: ${e.message}")
                }
            }

            // If we get here, nothing worked
            throw Exception("Story has no valid media data")

        } catch (e: Exception) {
            Log.e("camera_story", "Error loading story media: ${e.message}")
            Log.e("camera_story", "Stack trace: ${e.stackTraceToString()}")
            Toast.makeText(this, "Could not load story image: ${e.message}", Toast.LENGTH_SHORT).show()
            imageView.setImageResource(R.drawable.person1)
        }
    }
}