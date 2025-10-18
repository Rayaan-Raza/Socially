package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

fun loadBottomBarAvatar(navProfile: ImageView) {
    val uid = FirebaseAuth.getInstance().uid ?: return
    val ref = FirebaseDatabase.getInstance()
        .getReference("users")
        .child(uid)
        .child("profilePictureUrl")

    ref.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b64 = snapshot.getValue(String::class.java) ?: return

            val clean = b64.substringAfter(",", b64)  // remove data:image/... prefix
            val bytes = try {
                Base64.decode(clean, Base64.DEFAULT)
            } catch (_: Exception) { null } ?: return

            Glide.with(navProfile.context)
                .asBitmap()
                .load(bytes)
                .placeholder(R.drawable.oval)
                .error(R.drawable.oval)
                .circleCrop()
                .into(navProfile)
        }

        override fun onCancelled(error: DatabaseError) {}
    })
}

class following_page : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_following_page)
        val navProfile = findViewById<ImageView>(R.id.nav_profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tab_you).setOnClickListener {
            startActivity(Intent(this, you_page::class.java))
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