package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.google.android.material.button.MaterialButton

class switch_account : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_switch_account)

        findViewById<TextView>(R.id.Sign_up).setOnClickListener {
            startActivity(Intent(this, signup_page::class.java))
            finish()
        }

        findViewById<MaterialButton>(R.id.log_in).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }


    }

}
