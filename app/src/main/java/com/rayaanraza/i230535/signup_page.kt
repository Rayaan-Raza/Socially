package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.widget.ImageView

class signup_page : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_page)

        findViewById<ImageView>(R.id.left_arrow).setOnClickListener {
            startActivity(Intent(this, login_sign::class.java))
            finish()
        }


        findViewById<MaterialButton>(R.id.create_account).setOnClickListener {
            val intent = Intent(this, home_page::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
