package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class my_profile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, edit_profile::class.java))

        }

        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.nav_create).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.nav_like).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()
        }

        findViewById<FrameLayout>(R.id.friendsHighlightContainer).setOnClickListener {
            startActivity(Intent(this, story::class.java))
        }

        findViewById<FrameLayout>(R.id.profileImage).setOnClickListener {
            startActivity(Intent(this, my_story_view::class.java))
        }


    }
}