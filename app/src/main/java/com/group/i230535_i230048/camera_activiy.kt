package com.group.i230535_i230048

import android.Manifest
import android.content.ContentValues // CHANGED
import android.content.Context // CHANGED
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager // CHANGED
import android.net.NetworkCapabilities // CHANGED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64 // CHANGED
import android.util.Log // CHANGED
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
// REMOVED: Firebase imports
import com.group.i230535_i230048.AppDbHelper // CHANGED
import com.group.i230535_i230048.DB // CHANGED
import org.json.JSONObject // CHANGED
import java.io.ByteArrayOutputStream
import java.io.File

class camera_activiy : AppCompatActivity() {

    private var tempCameraUri: Uri? = null
    private var tempFile: File? = null

    // --- CHANGED: Added Volley, DB, and session ---
    private lateinit var queue: RequestQueue
    private lateinit var dbHelper: AppDbHelper
    private var currentUserId: String = ""
    // ---

    // (No changes to permission request)
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // (No changes to takePicture launcher)
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) {
                // CHANGED: Call new Volley upload function
                uploadStoryVolley(tempCameraUri!!)
            } else {
                Toast.makeText(this, "Camera cancelled.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_camera_activiy) // No layout needed

        enableEdgeToEdge()

        // --- CHANGED: Setup Volley, DB, and Session ---
        queue = Volley.newRequestQueue(this)
        dbHelper = AppDbHelper(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        currentUserId = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        // ---

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
        // (No changes here)
        val uri = tempCameraUri
        if (uri == null) {
            Toast.makeText(this, "Could not create image file.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        takePicture.launch(uri)
    }

    private fun createTempImageUri(): Uri? {
        // (No changes here)
        return try {
            val imagesDir = File(cacheDir, "images").apply { if (!exists()) mkdirs() }
            tempFile = File(imagesDir, "STORY_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                tempFile!!
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- CHANGED: Replaced 'uploadStoryToRealtimeDB' with 'uploadStoryVolley' ---
    private fun uploadStoryVolley(localUri: Uri) {
        val uid = currentUserId

        // 1) Decode bitmap from URI (Your existing logic is perfect)
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(contentResolver, localUri)
                ImageDecoder.decodeBitmap(src)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, localUri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read image: ${e.message}", Toast.LENGTH_LONG).show()
            finish(); return
        }

        // 2) Downscale + compress (Your existing logic is perfect)
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
        val base64 = android.util.Base64.encodeToString(bytes, Base64.NO_WRAP)

        // 3) Create API Payload
        val payloadParams = HashMap<String, String>()
        payloadParams["user_id"] = uid
        payloadParams["media_type"] = "image"
        payloadParams["imageBase64"] = base64 // Assuming API can take Base64

        val payloadJson = JSONObject()
        payloadJson.put("user_id", uid)
        payloadJson.put("media_type", "image")
        payloadJson.put("imageBase64", base64)

        // 4) Check network and send or queue
        if (isNetworkAvailable(this)) {
            val url = AppGlobals.BASE_URL + "upload_story.php" // (from ApiService.kt)
            val stringRequest = object : StringRequest(Request.Method.POST, url,
                { response ->
                    try {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            Toast.makeText(this, "Story uploaded!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Upload failed: ${json.getString("message")}", Toast.LENGTH_LONG).show()
                            saveToSyncQueue("upload_story.php", payloadJson) // Save on API error
                        }
                    } catch (e: Exception) {
                        Log.e("camera_activity", "Error parsing response: ${e.message}")
                        saveToSyncQueue("upload_story.php", payloadJson) // Save on parse error
                    } finally {
                        cleanupAndFinish()
                    }
                },
                { error ->
                    Log.e("camera_activity", "Volley error: ${error.message}")
                    saveToSyncQueue("upload_story.php", payloadJson) // Save on network error
                    cleanupAndFinish()
                }) {
                override fun getParams(): MutableMap<String, String> {
                    return payloadParams
                }
            }
            queue.add(stringRequest)
        } else {
            // 5) Offline - Save to queue
            saveToSyncQueue("upload_story.php", payloadJson)
            cleanupAndFinish()
        }
    }

    private fun cleanupAndFinish() {
        try { tempFile?.delete() } catch (_: Exception) {}
        finish()
    }

    // --- NEW: HELPER FUNCTIONS FOR OFFLINE QUEUE & NETWORK ---
    private fun saveToSyncQueue(endpoint: String, payload: JSONObject) {
        Log.d("camera_activity", "Saving to sync queue. Endpoint: $endpoint")
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues()
            cv.put(DB.SyncQueue.COLUMN_ENDPOINT, endpoint)
            cv.put(DB.SyncQueue.COLUMN_PAYLOAD, payload.toString())
            cv.put(DB.SyncQueue.COLUMN_STATUS, "PENDING")
            db.insert(DB.SyncQueue.TABLE_NAME, null, cv)

            runOnUiThread {
                Toast.makeText(this, "Offline. Story will upload later.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("camera_activity", "Failed to save to sync queue: ${e.message}")
        }
    }

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