package com.rayaanraza.i230535

import android.Manifest
import android.content.ContentUris

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.concurrent.thread

class posting : AppCompatActivity() {

    private lateinit var galleryGrid: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var mainImage: ImageView

    private var lastPickedUri: Uri? = null

    private val askPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadGallery()
        else Toast.makeText(this, "Gallery permission required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_posting)

        mainImage = findViewById(R.id.main_image)
        galleryGrid = findViewById(R.id.galleryGrid)

        findViewById<TextView>(R.id.cancel_btn).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }

        // 3-column gallery grid
        adapter = GalleryAdapter(mutableListOf()) { uri ->
            mainImage.setImageURI(uri)
            lastPickedUri = uri
            showCaptionDialog(uri)
        }
        galleryGrid.layoutManager = GridLayoutManager(this, 3)
        galleryGrid.adapter = adapter

        // Let user tap preview to post again
        mainImage.setOnClickListener {
            lastPickedUri?.let { showCaptionDialog(it) }
        }

        // “Next” uses the last selected image
        findViewById<TextView>(R.id.next_btn).setOnClickListener {
            val uri = lastPickedUri
            if (uri == null) {
                Toast.makeText(this, "Select a photo first", Toast.LENGTH_SHORT).show()
            } else {
                showCaptionDialog(uri)
            }
        }

        ensurePermissionAndLoad()
    }

    private fun ensurePermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED) {
            loadGallery()
        } else {
            askPermission.launch(permission)
        }
    }

    private fun loadGallery() {
        thread {
            val uris = mutableListOf<Uri>()
            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            contentResolver.query(collection, projection, null, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    uris.add(uri)
                }
            }

            runOnUiThread {
                adapter.submit(uris.take(200))
                if (uris.isNotEmpty()) {
                    lastPickedUri = uris.first()
                    mainImage.setImageURI(lastPickedUri)
                }
            }
        }
    }

    private fun showCaptionDialog(uri: Uri) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_caption, null)
        val preview = view.findViewById<ImageView>(R.id.caption_preview)
        val input = view.findViewById<EditText>(R.id.caption_input)

        preview.setImageURI(uri)

        AlertDialog.Builder(this)
            .setTitle("Add a caption")
            .setView(view)
            .setPositiveButton("Post") { d, _ ->
                val caption = input.text.toString().trim()
                savePostToDb(uri, caption)
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    private fun savePostToDb(uri: Uri, caption: String) {
        val progressDialog = AlertDialog.Builder(this)
            .setMessage("Posting...")
            .setCancelable(false)
            .show()

        thread {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                runOnUiThread { progressDialog.dismiss() }
                return@thread
            }
            val username = auth.currentUser?.displayName ?: "user"

            val postId = UUID.randomUUID().toString()
            val base64 = encodeImageToBase64(uri)
            if (base64 == null) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            val now = System.currentTimeMillis()
            val post = mapOf(
                "postId" to postId,
                "uid" to uid,
                "username" to username,
                "imageBase64" to base64,
                "caption" to caption,
                "createdAt" to now,
                "likeCount" to 0,
                "commentCount" to 0
            )

            val db = FirebaseDatabase.getInstance().reference
            val updates = hashMapOf<String, Any>(
                "/posts/$uid/$postId" to post,
                "/postIndex/$postId" to mapOf("uid" to uid, "createdAt" to now)
            )

            db.updateChildren(updates).addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Posted!", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "DB error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(src)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos) // keep DB small
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    // ---------- Gallery Adapter ----------
    private class GalleryAdapter(
        private val data: MutableList<Uri>,
        private val onClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val img: ImageView = itemView.findViewById(R.id.img)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = layoutInflater(parent).inflate(
                R.layout.item_gallery_thumb, parent, false
            )
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = data[position]
            holder.img.setImageURI(uri)
            holder.img.setOnClickListener { onClick(uri) }
        }

        override fun getItemCount(): Int = data.size

        fun submit(items: List<Uri>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        private fun layoutInflater(parent: ViewGroup) =
            LayoutInflater.from(parent.context)
    }
}
