package com.group.i230535_i230048

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.concurrent.thread
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Base64
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide


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
        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        mainImage  = findViewById(R.id.main_image)
        galleryGrid = findViewById(R.id.galleryGrid)

        findViewById<TextView>(R.id.cancel_btn).setOnClickListener {
            startActivity(Intent(this, home_page::class.java))
            finish()
        }

        // 3-column gallery grid
        adapter = GalleryAdapter(mutableListOf()) { uri ->
            lastPickedUri = uri
            // Use Glide for async preview (no UI jank)
            Glide.with(this).load(uri).into(mainImage)
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
                        Glide.with(this).load(lastPickedUri).into(mainImage)
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

        // async preview with Glide (prevents UI jank)
        Glide.with(this).load(uri).into(preview)

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

                // Launch the upload off the UI thread
                uploadPost(
                    uri = lastPickedUri!!,
                    caption = caption,
                    onDone = {
                        isUploading = false
                        postBtn.isEnabled = true
                        cancelBtn.isEnabled = true
                        // return success so home_page can refresh
                        setResult(RESULT_OK)
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
     * Uploads image to RTDB (Base64) and writes metadata.
     * Heavy work (decode/compress) is off the UI thread.
     */
    private fun uploadPost(
        uri: Uri,
        caption: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return onError("Not authenticated")
        val db = FirebaseDatabase.getInstance().reference
        val postId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                // 1) Heavy work off main thread: decode+downsample+JPEG+Base64
                val base64 = withContext(Dispatchers.IO) {
                    encodeUriToBase64Safe(uri, maxWidth = 1080, jpegQuality = 80)
                }
                if (base64.isEmpty()) {
                    onError("Could not read/encode image")
                    return@launch
                }

                // 2) Read username (await, non-blocking)
                val username = try {
                    val snap = db.child("users").child(uid).child("username").get().await()
                    (snap.getValue(String::class.java) ?: "").ifBlank { "user" }
                } catch (e: Exception) {
                    "user"
                }

                // 3) Compose post & write to RTDB
                val post = mapOf(
                    "postId" to postId,
                    "uid" to uid,
                    "username" to username,
                    "imageUrl" to "",
                    "imageBase64" to base64,
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

                db.updateChildren(updates).await()

                Toast.makeText(this@posting, "Posted!", Toast.LENGTH_SHORT).show()
                onDone()

            } catch (oom: OutOfMemoryError) {
                onError("Image too large (OOM). Try a smaller one.")
            } catch (e: Exception) {
                onError("Post failed: ${e.message}")
            }
        }
    }

    private fun encodeUriToBase64Safe(
        uri: Uri,
        maxWidth: Int,
        jpegQuality: Int
    ): String {
        try {
            // --- Path A: Modern, reliable decoder (API 28+) ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    val scale = if (w > maxWidth) maxWidth.toFloat() / w else 1f
                    val tw = (w * scale).toInt().coerceAtLeast(1)
                    val th = (h * scale).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(tw, th)
                    // Let ImageDecoder handle orientation/EXIF
                }
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val bytes = baos.toByteArray()
                baos.close()
                bmp.recycle()
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            // --- Path B: Legacy devices (BitmapFactory with inSampleSize) ---
            // 1) Read bounds
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, optsBounds)
            }

            var srcW = optsBounds.outWidth
            var srcH = optsBounds.outHeight

            // Some formats/URIs don’t populate bounds — fallback decode once
            if (srcW <= 0 || srcH <= 0) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val tmp = BitmapFactory.decodeStream(input)
                    if (tmp != null) {
                        srcW = tmp.width
                        srcH = tmp.height
                        tmp.recycle()
                    }
                }
            }
            if (srcW <= 0 || srcH <= 0) {
                // Final fallback handled below (Glide)
                throw IllegalStateException("Bounds not readable")
            }

            var inSampleSize = 1
            if (srcW > maxWidth) {
                val ratio = Math.ceil(srcW.toDouble() / maxWidth.toDouble()).toInt().coerceAtLeast(1)
                inSampleSize = Integer.highestOneBit(ratio).coerceAtLeast(1)
            }

            val optsDecode = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val sampled = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, optsDecode)
            }

            if (sampled != null) {
                val finalBmp = if (sampled.width > maxWidth) {
                    val ratio = sampled.height.toFloat() / sampled.width.toFloat()
                    val h = (maxWidth * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(sampled, maxWidth, h, true)
                } else sampled

                val baos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                if (finalBmp !== sampled) sampled.recycle()
                val out = baos.toByteArray()
                baos.close()
                if (finalBmp !== sampled) finalBmp.recycle()
                return Base64.encodeToString(out, Base64.NO_WRAP)
            }

            // --- Path C: Ultimate fallback via Glide (handles cloud/HEIC/etc.) ---
            val glideBmp = Glide.with(this)
                .asBitmap()
                .load(uri)
                .submit(maxWidth, maxWidth) // Glide will keep aspect ratio
                .get() // called on Dispatchers.IO by our caller

            val baos = ByteArrayOutputStream()
            glideBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            val bytes = baos.toByteArray()
            baos.close()
            glideBmp.recycle()
            return Base64.encodeToString(bytes, Base64.NO_WRAP)

        } catch (e: OutOfMemoryError) {
            return ""
        } catch (e: Exception) {
            // As a last attempt, try Glide if not already tried
            return try {
                val glideBmp = Glide.with(this)
                    .asBitmap()
                    .load(uri)
                    .submit(maxWidth, maxWidth)
                    .get()
                val baos = ByteArrayOutputStream()
                glideBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val bytes = baos.toByteArray()
                baos.close()
                glideBmp.recycle()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (_: Exception) {
                ""
            }
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
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_thumb, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val uri = data[position]
            // use Glide for thumbnails as well (smooth scroll)
            Glide.with(holder.img.context).load(uri).into(holder.img)
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
