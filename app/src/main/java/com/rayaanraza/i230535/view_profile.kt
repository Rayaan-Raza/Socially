package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class view_profile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.navSearch).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.navCreate).setOnClickListener {
            startActivity(Intent(this, posting::class.java))
            finish()

        }

        findViewById<TextView>(R.id.followingButton).setOnClickListener {
            startActivity(Intent(this, view_profile_2::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.navLike).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
            finish()

        }


    }
}