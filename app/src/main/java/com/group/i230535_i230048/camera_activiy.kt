package com.group.i230535_i230048

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class camera_activiy : AppCompatActivity() {

    private var tempCameraUri: Uri? = null
    private var tempFile: File? = null

    private lateinit var queue: RequestQueue
    private lateinit var dbHelper: AppDbHelper
    private var currentUserId: String = ""

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) {
                uploadStoryVolley(tempCameraUri!!)
            } else {
                Toast.makeText(this, "Camera cancelled.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        queue = Volley.newRequestQueue(this)
        dbHelper = AppDbHelper(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tempCameraUri = createTempImageUri()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val uri = tempCameraUri
        if (uri == null) {
            Toast.makeText(this, "Could not create image file.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        takePicture.launch(uri)
    }

    private fun createTempImageUri(): Uri? {
        return try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            tempFile = File(imagesDir, "STORY_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                tempFile!!
            )
        } catch (e: Exception) {
            Log.e("camera_activity", "Error creating temp file: ${e.message}")
            null
        }
    }

    // API REFERENCE: Section 4.1 - stories_upload.php
    // POST stories_upload.php
    // Body: uid (required), mediaBase64 (required)
    // Generates storyId, createdAt, expiresAt = createdAt + 24h
    private fun uploadStoryVolley(localUri: Uri) {
        val uid = currentUserId

        // 1) Decode bitmap from URI
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(contentResolver, localUri)
                ImageDecoder.decodeBitmap(src)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, localUri)
            }
        } catch (e: Exception) {
            Log.e("camera_activity", "Could not read image: ${e.message}")
            Toast.makeText(this, "Could not read image: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2) Downscale + compress
        val maxSide = 900
        val w = bitmap.width
        val h = bitmap.height
        val scale = (maxOf(w, h).toDouble() / maxSide).coerceAtLeast(1.0)
        val scaled = if (scale > 1.0) {
            Bitmap.createScaledBitmap(bitmap, (w / scale).toInt(), (h / scale).toInt(), true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val bytes = baos.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        // 3) Create API Payload
        val payloadJson = JSONObject()
        payloadJson.put("uid", uid)
        payloadJson.put("mediaBase64", base64)

        // 4) Check network and send or queue
        if (isNetworkAvailable(this)) {
            Log.d("camera_activity", "Uploading story online...")

            val url = AppGlobals.BASE_URL + "stories_upload.php"
            val stringRequest = object : StringRequest(
                Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Log.d("camera_activity", "Story uploaded successfully")
                            Toast.makeText(this, "Story uploaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            val errorMsg = json.optString("message", "Unknown error")
                            Log.w("camera_activity", "Upload failed: $errorMsg")
                            Toast.makeText(this, "Upload failed: $errorMsg", Toast.LENGTH_LONG).show()
                            saveToSyncQueue("stories_upload.php", payloadJson)
                        }
                    } catch (e: Exception) {
                        Log.e("camera_activity", "Error parsing response: ${e.message}")
                        saveToSyncQueue("stories_upload.php", payloadJson)
                    } finally {
                        cleanupAndFinish()
                    }
                },
                { error ->
                    Log.e("camera_activity", "Volley error: ${error.message}")
                    Toast.makeText(this, "Network error. Story will upload later.", Toast.LENGTH_SHORT).show()
                    saveToSyncQueue("stories_upload.php", payloadJson)
                    cleanupAndFinish()
                }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["uid"] = uid
                    params["mediaBase64"] = base64
                    return params
                }
            }
            queue.add(stringRequest)
        } else {
            // 5) Offline - Save to queue
            Log.d("camera_activity", "Offline - saving story to sync queue")
            Toast.makeText(this, "Offline. Story will upload later.", Toast.LENGTH_SHORT).show()
            saveToSyncQueue("stories_upload.php", payloadJson)
            cleanupAndFinish()
        }
    }

    private fun cleanupAndFinish() {
        try {
            tempFile?.delete()
        } catch (e: Exception) {
            Log.e("camera_activity", "Error deleting temp file: ${e.message}")
        }
        finish()
    }

    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("camera_activity", "Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)
            Log.d("camera_activity", "Saved to sync queue successfully")
        } catch (e: Exception) {
            Log.e("camera_activity", "Failed to save to sync queue: ${e.message}")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val activeNetwork =
                    connectivityManager.getNetworkCapabilities(network) ?: return false
                when {
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> false
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                networkInfo.isConnected
            }
        } catch (e: Exception) {
            Log.e("camera_activity", "Error checking network: ${e.message}")
            false
        }
    }
}
