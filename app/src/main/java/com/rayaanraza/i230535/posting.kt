package com.rayaanraza.i230535

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlin.concurrent.thread

class posting : AppCompatActivity() {

    private lateinit var galleryGrid: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var mainImage: ImageView

    private var lastPickedUri: Uri? = null
    private var isUploading = false

    private val askPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Gallery permission granted", Toast.LENGTH_SHORT).show()
            loadGallery()
        } else {
            Toast.makeText(this, "Gallery permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_posting)

        mainImage  = findViewById(R.id.main_image)
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

    // -------- Permissions & Gallery --------

    private fun ensurePermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadGallery()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "We need access to your photos to pick an image.", Toast.LENGTH_LONG).show()
                askPermission.launch(permission)
            }
            else -> {
                askPermission.launch(permission)
            }
        }
    }

    private fun loadGallery() {
        thread {
            runCatching {
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
            }.onFailure {
                runOnUiThread {
                    Toast.makeText(this, "Failed to load gallery: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -------- Caption dialog & Upload --------

    private fun showCaptionDialog(uri: Uri) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_caption, null)
        val preview = view.findViewById<ImageView>(R.id.caption_preview)
        val input   = view.findViewById<EditText>(R.id.caption_input)

        // Make sure it’s visible & editable
        input.isEnabled = true
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.requestFocus()

        preview.setImageURI(uri)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add a caption")
            .setView(view)
            .setPositiveButton("Post", null)  // override click below
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val postBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            postBtn.setOnClickListener {
                if (isUploading) return@setOnClickListener
                val caption = input.text?.toString()?.trim().orEmpty()
                if (lastPickedUri == null) {
                    Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Disable while uploading
                isUploading = true
                postBtn.isEnabled = false
                cancelBtn.isEnabled = false
                uploadPost(lastPickedUri!!, caption,
                    onDone = {
                        isUploading = false
                        postBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                        dialog.dismiss()
                        finish()
                    },
                    onError = { msg ->
                        isUploading = false
                        postBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }

        dialog.show()
    }

    /**
     * Uploads image to Firebase Storage and writes metadata to Realtime Database.
     * Mirrors your story approach.
     */
// 1) Replace your existing uploadPost(...) with this RTDB-only version
    private fun uploadPost(
        uri: Uri,
        caption: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return onError("Not authenticated")

        // Convert the picked image -> Base64 (reuse your existing helper)
        val base64 = encodeUriToBase64(uri, maxWidth = 1080, jpegQuality = 80)
        if (base64.isEmpty()) return onError("Could not read image")

        val postId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val db = FirebaseDatabase.getInstance().reference

        // ✅ Get username from /users/{uid}/username instead of displayName
        db.child("users").child(uid).child("username").get()
            .addOnSuccessListener { snap ->
                val username = (snap.getValue(String::class.java) ?: "").ifBlank { "user" }

                val post = mapOf(
                    "postId" to postId,
                    "uid" to uid,
                    "username" to username,      // ✅ now filled
                    "imageUrl" to "",
                    "imageBase64" to base64,     // RTDB-only flow
                    "caption" to caption,
                    "createdAt" to now,
                    "likeCount" to 0,
                    "commentCount" to 0
                )

                val updates = hashMapOf<String, Any>(
                    "/posts/$uid/$postId" to post,
                    "/postIndex/$postId" to mapOf(
                        "postId" to postId,
                        "uid" to uid,
                        "createdAt" to now
                    )
                )

                db.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Posted!", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                    .addOnFailureListener { e -> onError("DB_UPDATE failed: ${e.message}") }
            }
            .addOnFailureListener { e -> onError("USERNAME_READ failed: ${e.message}") }
    }

    // 2) Add this helper below uploadPost(...)
    private fun encodeUriToBase64(
        uri: Uri,
        maxWidth: Int,
        jpegQuality: Int
    ): String {
        // Decode original
        val original = contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: return ""

        // Scale down if needed
        val w = original.width
        val h = original.height
        val scale = if (w > maxWidth) maxWidth.toFloat() / w.toFloat() else 1f
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                original, (w * scale).toInt(), (h * scale).toInt(), true
            )
        } else original

        // Compress to JPEG
        val baos = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality, baos)
        if (scaled !== original) original.recycle()

        val bytes = baos.toByteArray()
        baos.close()

        // Base64 (no line breaks)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
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
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_thumb, parent, false)
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
    }
}
