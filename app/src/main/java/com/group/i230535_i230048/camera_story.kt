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

        val img = findViewById<ImageView>(R.id.storyImage)
        val close = findViewById<ImageView>(R.id.closeBtn)
        close.setOnClickListener { finish() }

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val myUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        val targetUid = intent.getStringExtra("uid") ?: myUid

        if (targetUid.isEmpty()) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val url = AppGlobals.BASE_URL + "get_stories.php?user_id=$targetUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        // --- CORRECTED: Use your Story_data class ---
                        val listType = object : TypeToken<List<Story_data>>() {}.type
                        val stories: List<Story_data> = Gson().fromJson(dataArray.toString(), listType)
                        // ---

                        // Replicate original logic: Find latest, unexpired story
                        val now = System.currentTimeMillis()

                        // --- CORRECTED: Use properties from Story_data ---
                        val latestStory = stories
                            .filter { it.expiresAt > now }
                            .maxByOrNull { it.createdAt }
                        // ---

                        if (latestStory == null) {
                            Toast.makeText(this, "No active stories!", Toast.LENGTH_SHORT).show()
                            finish()
                            return@StringRequest
                        }

                        // --- CORRECTED: Only use mediaUrl ---
                        if (latestStory.mediaUrl.isNotEmpty()) {
                            // Your Story_data model only has mediaUrl, so we use Glide
                            Glide.with(this).load(latestStory.mediaUrl).into(img)
                        } else {
                            // The model does not have Base64, so this is an error
                            throw Exception("Story media is empty")
                        }
                        // ---

                    } else {
                        Toast.makeText(this, "No active stories!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("camera_story", "Error parsing stories: ${e.message}")
                    Toast.makeText(this, "Error loading story.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            { error ->
                Log.e("camera_story", "Volley error: ${error.message}")
                Toast.makeText(this, "Failed to load story.", Toast.LENGTH_LONG).show()
                finish()
            }
        )
        queue.add(stringRequest)
    }
}