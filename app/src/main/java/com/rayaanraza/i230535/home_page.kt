package com.rayaanraza.i230535

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment

class home_page : AppCompatActivity() {

    private lateinit var likeImageView: ImageView
    private lateinit var searchImageView: ImageView
    private lateinit var dmsImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        likeImageView = findViewById(R.id.heart)
        searchImageView = findViewById(R.id.search)
        dmsImageView = findViewById(R.id.dms)

        likeImageView.setOnClickListener {
            val isLiked = likeImageView.drawable.constantState == resources.getDrawable(R.drawable.like).constantState
            if (isLiked) {
                likeImageView.setImageResource(R.drawable.liked) // Change to liked image
                Toast.makeText(this, "Liked!", Toast.LENGTH_SHORT).show()
            } else {
                likeImageView.setImageResource(R.drawable.like) // Change back to like image
                Toast.makeText(this, "Unliked!", Toast.LENGTH_SHORT).show()
            }
        }

        searchImageView.setOnClickListener {
            val navController = findNavController(R.id.search)
            navController.navigate(R.id.search)
        }

        dmsImageView.setOnClickListener {
            val navController = findNavController(R.id.dms)
            navController.navigate(R.id.dms)
        }
    }

    private fun findNavController(id: Int): NavController {
        val navHostFragment = supportFragmentManager.findFragmentById(id) as NavHostFragment
        return navHostFragment.navController
    }
}
