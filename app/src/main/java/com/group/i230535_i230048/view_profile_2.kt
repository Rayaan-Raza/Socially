package com.group.i230535_i230048

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.group.i230535_i230048.R.id.main

class view_profile_2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_profile2)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.followingButton).setOnClickListener {
            startActivity(Intent(this, view_profile::class.java))
            finish()

        }

        findViewById<TextView>(R.id.backButton).setOnClickListener {
            startActivity(Intent(this, specific_search::class.java))
            finish()

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