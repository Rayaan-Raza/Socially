package com.rayaanraza.i230535

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class login_sign : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_sign)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.log_in).setOnClickListener {
            try {
                startActivity(Intent(this, home_page::class.java))
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        findViewById<TextView>(R.id.switch_acc).setOnClickListener {
            try {
                startActivity(Intent(this, switch_account::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        findViewById<TextView>(R.id.Sign_up).setOnClickListener {
            try {
                startActivity(Intent(this, signup_page::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


}
