package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rayaanraza.i230535.R.id.clickable
import com.rayaanraza.i230535.R.id.main

class dms : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dms)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        findViewById<LinearLayout>(clickable).setOnClickListener {
            startActivity(Intent(this, chat::class.java))
            finish()

        }

        findViewById<ImageView>(R.id.back).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()

        }
    }
}