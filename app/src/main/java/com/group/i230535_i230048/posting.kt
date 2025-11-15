package com.group.i230535_i230048

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues // CHANGED: Added for SQLite
import android.content.Context // CHANGED: Added for Network/Prefs
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager // CHANGED: Added for Network
import android.net.NetworkCapabilities // CHANGED: Added for Network
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
// REMOVED: import com.google.firebase.auth.FirebaseAuth
// REMOVED: import com.google.firebase.database.FirebaseDatabase
// REMOVED: import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.concurrent.thread
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Base64
import android.util.Log // CHANGED: Added for logging
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.android.volley.Request // CHANGED: Added Volley
import com.android.volley.toolbox.StringRequest // CHANGED: Added Volley
import com.android.volley.toolbox.Volley // CHANGED: Added Volley
import com.group.i230535_i230048.AppDbHelper // CHANGED: Added DB Helper
import com.group.i230535_i230048.DB // CHANGED: Added DB Helper
import org.json.JSONObject // CHANGED: Added JSON


class posting : AppCompatActivity() {

    private lateinit var galleryGrid: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private lateinit var mainImage: ImageView

    private var lastPickedUri: Uri? = null
    private var isUploading = false

    // CHANGED: Added DB Helper and Volley Queue
    private lateinit var dbHelper: AppDbHelper

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

        // CHANGED: Initialize DB Helper
        dbHelper = AppDbHelper(this)

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

        // 3-column gallery grid (No changes)
        adapter = GalleryAdapter(mutableListOf()) { uri ->
            lastPickedUri = uri
            Glide.with(this).load(uri).into(mainImage)
            showCaptionDialog(uri)
        }
        galleryGrid.layoutManager = GridLayoutManager(this, 3)
        galleryGrid.adapter = adapter

        // Let user tap preview to post again (No changes)
        mainImage.setOnClickListener {
            lastPickedUri?.let { showCaptionDialog(it) }
        }

        // “Next” uses the last selected image (No changes)
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

    // -------- Permissions & Gallery (No changes) --------

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

    // -------- Caption dialog (No changes to this function) --------

    private fun showCaptionDialog(uri: Uri) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_caption, null)
        val preview = view.findViewById<ImageView>(R.id.caption_preview)
        val input   = view.findViewById<EditText>(R.id.caption_input)

        input.isEnabled = true
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.requestFocus()

        Glide.with(this).load(uri).into(preview)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add a caption")
            .setView(view)
            .setPositiveButton("Post", null)
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

                isUploading = true
                postBtn.isEnabled = false
                cancelBtn.isEnabled = false

                // CHANGED: Swapped Firebase function with new offline-first function
                uploadPostVolley(
                    uri = lastPickedUri!!,
                    caption = caption,
                    onDone = {
                        isUploading = false
                        postBtn.isEnabled = true
                        cancelBtn.isEnabled = true
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
     * CHANGED: Replaced 'uploadPost' with 'uploadPostVolley'
     * Encodes image to Base64, then attempts to upload via Volley.
     * If offline or Volley fails, it saves the post to the local sync queue.
     */
    private fun uploadPostVolley(
        uri: Uri,
        caption: String,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        // 1. Get user details from SharedPreferences
        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(AppGlobals.KEY_USER_UID, null)
        val username = prefs.getString(AppGlobals.KEY_USERNAME, "user")

        if (uid == null) {
            onError("Not authenticated")
            return
        }

        val postId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                // 2. Heavy work: encode image to Base64 (using your helper)
                val base64 = withContext(Dispatchers.IO) {
                    encodeUriToBase64Safe(uri, maxWidth = 1080, jpegQuality = 80)
                }
                if (base64.isEmpty()) {
                    onError("Could not read/encode image")
                    return@launch
                }

                // 3. Create payload for Volley and Sync Queue
                val payloadParams = HashMap<String, String>()
                payloadParams["uid"] = uid
                payloadParams["username"] = username ?: "user"
                payloadParams["caption"] = caption
                payloadParams["imageBase64"] = base64
                payloadParams["createdAt"] = now.toString()
                payloadParams["postId"] = postId

                // Create a JSON version for the offline queue
                val payloadJson = JSONObject()
                payloadParams.forEach { (key, value) -> payloadJson.put(key, value) }

                // 4. Check network and attempt upload
                if (isNetworkAvailable(this@posting)) {
                    val queue = Volley.newRequestQueue(this@posting)
                    val url = AppGlobals.BASE_URL + "create_post.php" // (from ApiService.kt)

                    val stringRequest = object : StringRequest(
                        Request.Method.POST,
                        url,
                        { response ->
                            // Online - Success
                            try {
                                val json = JSONObject(response)
                                if (json.getBoolean("success")) {
                                    Toast.makeText(this@posting, "Posted!", Toast.LENGTH_SHORT).show()
                                    onDone()
                                } else {
                                    onError("Post failed: ${json.getString("message")}")
                                }
                            } catch (e: Exception) {
                                onError("Error parsing response: ${e.message}")
                            }
                        },
                        { error ->
                            // Online - Network Error (e.g., server down)
                            // Save to queue to try again later
                            Log.e("posting.kt", "Volley error, saving to queue: ${error.message}")
                            saveToSyncQueue("create_post.php", payloadJson)
                            onDone() // Finish activity, user assumes it's fine
                        }) {

                        override fun getParams(): MutableMap<String, String> {
                            return payloadParams
                        }
                    }
                    queue.add(stringRequest)
                } else {
                    // 5. Offline - Save to queue immediately
                    saveToSyncQueue("create_post.php", payloadJson)
                    onDone() // Finish activity, user assumes it's fine
                }

            } catch (oom: OutOfMemoryError) {
                onError("Image too large (OOM). Try a smaller one.")
            } catch (e: Exception) {
                onError("Post failed: ${e.message}")
            }
        }
    }

    // This is your excellent Base64 encoder. No changes needed.
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
                }
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                val bytes = baos.toByteArray()
                baos.close()
                bmp.recycle()
                return Base64.encodeToString(bytes, Base64.NO_WRAP)
            }

            // --- Path B: Legacy devices (BitmapFactory with inSampleSize) ---
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, optsBounds)
            }
            var srcW = optsBounds.outWidth
            var srcH = optsBounds.outHeight
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
            if (srcW <= 0 || srcH <= 0) throw IllegalStateException("Bounds not readable")

            var inSampleSize = 1
            if (srcW > maxWidth) {
                inSampleSize = Integer.highestOneBit(Math.ceil(srcW.toDouble() / maxWidth.toDouble()).toInt())
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
                .submit(maxWidth, maxWidth)
                .get()

            val baos = ByteArrayOutputStream()
            glideBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            val bytes = baos.toByteArray()
            baos.close()
            glideBmp.recycle()
            return Base64.encodeToString(bytes, Base64.NO_WRAP)

        } catch (e: OutOfMemoryError) {
            return ""
        } catch (e: Exception) {
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


    // ---------- Gallery Adapter (No changes) ----------
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

    // --- CHANGED: ADDED HELPER FUNCTIONS ---

    /**
     * Saves a failed or offline request to the SQLite sync queue.
     */
    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("posting.kt", "Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            runOnUiThread {
                Toast.makeText(this, "Offline. Post will send later.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("posting.kt", "Failed to save to sync queue: ${e.message}")
        }
    }

    /**
     * Checks if the device is connected to the internet.
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
}