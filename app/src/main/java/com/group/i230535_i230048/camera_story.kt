package com.group.i230535_i230048

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class camera_story : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_story)
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val img = findViewById<ImageView>(R.id.storyImage)
        val close = findViewById<ImageView>(R.id.closeBtn)
        close.setOnClickListener { finish() }

        // If you want to open someone else’s story, pass "uid" in the intent.
        val targetUid = intent.getStringExtra("uid")
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: run {
                Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show()
                finish(); return
            }

        val ref = FirebaseDatabase.getInstance()
            .getReference("stories")
            .child(targetUid)

        ref.get()
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()

                // Find the latest (max createdAt) story that hasn't expired.
                var bestBase64: String? = null
                var bestCreatedAt = Long.MIN_VALUE

                for (storySnap in snapshot.children) {
                    val expiresAt = storySnap.child("expiresAt").getValue(Long::class.java) ?: 0L
                    val createdAt = storySnap.child("createdAt").getValue(Long::class.java) ?: 0L
                    val base64 = storySnap.child("imageBase64").getValue(String::class.java)

                    if (expiresAt > now && !base64.isNullOrEmpty() && createdAt > bestCreatedAt) {
                        bestCreatedAt = createdAt
                        bestBase64 = base64
                    }
                }

                if (bestBase64 == null) {
                    Toast.makeText(this, "No active stories!", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                try {
                    // We encoded with NO_WRAP; decode the same way.
                    val bytes = Base64.decode(bestBase64, Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        img.setImageBitmap(bmp)
                    } else {
                        Toast.makeText(this, "Corrupt image data.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error decoding image.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load story: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
