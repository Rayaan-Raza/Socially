package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rayaanraza.i230535.R.id.post

class story_post : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_story_post)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<RelativeLayout>(R.id.item1).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }

        findViewById<ImageView>(post).setOnClickListener {
            startActivity(Intent(this, my_story_view::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.post_2).setOnClickListener {
            startActivity(Intent(this, my_story_view::class.java))
            finish()
        }


    }
}