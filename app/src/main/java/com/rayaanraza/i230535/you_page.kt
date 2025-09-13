package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class you_page : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_you_page)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tab_following).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()

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

        findViewById<ImageView>(R.id.nav_profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java))
            finish()

        }


    }
}