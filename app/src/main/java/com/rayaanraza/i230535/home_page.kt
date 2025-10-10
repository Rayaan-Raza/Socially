package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class home_page : AppCompatActivity() {

    private var isPostLiked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        findViewById<ImageView>(R.id.like).setOnClickListener { postLikeImageView ->
            isPostLiked = !isPostLiked
            (postLikeImageView as ImageView).setImageResource(
                if (isPostLiked) R.drawable.liked else R.drawable.like
            )
        }

        findViewById<ImageView>(R.id.heart).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
        }

        findViewById<FrameLayout>(R.id.story_clickable).setOnClickListener {
            startActivity(Intent(this, story_view::class.java))
            finish()
        }

        findViewById<ImageView>(R.id.search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
            finish()
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

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        findViewById<ImageView>(R.id.ring1).setOnClickListener {
            startActivity(
                Intent(this, camera_story::class.java)
                    .putExtra("uid", uid)
            )
        }



        findViewById<ImageView>(R.id.profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
        }
    }
}
