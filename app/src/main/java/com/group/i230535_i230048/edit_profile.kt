package com.group.i230535_i230048

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class edit_profile : AppCompatActivity() {

    private lateinit var dbHelper: AppDbHelper
    private lateinit var queue: RequestQueue
    private var currentUserId: String = ""

    private lateinit var profileImage: ImageView
    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etWebsite: EditText
    private lateinit var etBio: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etGender: EditText
    private lateinit var btnCancel: TextView
    private lateinit var btnDone: TextView
    private lateinit var txtChangePhoto: TextView

    private var newProfileImageBase64: String? = null
    private val PICK_IMAGE = 103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = AppDbHelper(this)
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initViews() {
        profileImage = findViewById(R.id.img_profile)
        etName = findViewById(R.id.et_name)
        etUsername = findViewById(R.id.et_username)
        etWebsite = findViewById(R.id.et_website)
        etBio = findViewById(R.id.et_bio)
        etEmail = findViewById(R.id.et_email)
        etPhone = findViewById(R.id.et_phone)
        etGender = findViewById(R.id.et_gender)
        btnCancel = findViewById(R.id.btn_cancel)
        btnDone = findViewById(R.id.btn_done)
        txtChangePhoto = findViewById(R.id.txt_change_photo)
    }

    private fun setupClickListeners() {
        btnCancel.setOnClickListener { finish() }
        btnDone.setOnClickListener { saveProfile() }
        txtChangePhoto.setOnClickListener { openGallery() }
        profileImage.setOnClickListener { openGallery() }
        findViewById<TextView>(R.id.btn_switch_pro).setOnClickListener {
            Toast.makeText(this, "Professional account coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        Log.d("edit_profile", "Loading user data from DB...")
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.User.TABLE_NAME, null,
            "${DB.User.COLUMN_UID} = ?", arrayOf(currentUserId),
            null, null, null
        )

        if (cursor.moveToFirst()) {
            etName.setText(cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_FULL_NAME)))
            etUsername.setText(cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_USERNAME)))
            etBio.setText(cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_BIO)))
            etEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_EMAIL)))

            profileImage.loadUserAvatar(currentUserId, currentUserId, R.drawable.default_avatar)
        } else {
            Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show()
        }
        cursor.close()
    }

    private fun saveProfile() {
        val fullName = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val website = etWebsite.text.toString().trim()
        val bio = etBio.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val gender = etGender.text.toString().trim()

        if (fullName.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Name and username are required", Toast.LENGTH_SHORT).show()
            return
        }

        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: fullName
        val lastName = nameParts.getOrNull(1) ?: ""

        val params = HashMap<String, String>()
        params["uid"] = currentUserId
        params["fullName"] = fullName
        params["firstName"] = firstName
        params["lastName"] = lastName
        params["username"] = username
        params["bio"] = bio
        if (website.isNotEmpty()) params["website"] = website
        if (email.isNotEmpty()) params["email"] = email
        if (phone.isNotEmpty()) params["phoneNumber"] = phone
        if (gender.isNotEmpty()) params["gender"] = gender
        if (newProfileImageBase64 != null) {
            params["profileImageBase64"] = newProfileImageBase64!!
        }

        val cv = ContentValues()
        cv.put(DB.User.COLUMN_FULL_NAME, fullName)
        cv.put(DB.User.COLUMN_USERNAME, username)
        cv.put(DB.User.COLUMN_BIO, bio)
        cv.put(DB.User.COLUMN_EMAIL, email)

        // API Spec: user_profile_update.php
        val url = AppGlobals.BASE_URL + "user_profile_update.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        dbHelper.writableDatabase.update(
                            DB.User.TABLE_NAME, cv,
                            "${DB.User.COLUMN_UID} = ?", arrayOf(currentUserId)
                        )

                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to update: ${json.getString("message")}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error parsing response: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            { error ->
                Toast.makeText(this, "Network error: ${error.message}", Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK && data?.data != null) {
            val imageUri: Uri = data.data!!
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    val source = ImageDecoder.createSource(contentResolver, imageUri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                }
                profileImage.setImageBitmap(bitmap)
                newProfileImageBase64 = encodeImage(bitmap)
                Toast.makeText(this, "Photo selected. Click Done to save.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
        val imageBytes = output.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }
}