package com.rayaanraza.i230535

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.provider.MediaStore // <- only used on API < 28 (suppressed)

class camera_activiy : AppCompatActivity() {

    private var tempCameraUri: Uri? = null
    private var tempFile: File? = null

    // CAMERA permission
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera()
            else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // Take a picture into our Uri
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempCameraUri != null) {
                uploadStoryToRealtimeDB(tempCameraUri!!)
            } else {
                Toast.makeText(this, "Camera cancelled.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_camera_activiy) // optional

        // Must be logged in
        if (FirebaseAuth.getInstance().currentUser?.uid == null) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Prepare temp Uri
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
            finish(); return
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
            null
        }
    }

    private fun uploadStoryToRealtimeDB(localUri: Uri) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

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
            Toast.makeText(this, "Could not read image: ${e.message}", Toast.LENGTH_LONG).show()
            finish(); return
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
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        // 3) Build node (under stories/{uid}/{storyId})
        val now = System.currentTimeMillis()
        val storyId = now.toString()
        val storyNode = mapOf(
            "storyId" to storyId,
            "userId" to uid,                      // <- add userId
            "mediaType" to "image",
            "createdAt" to now,
            "expiresAt" to (now + 24L * 60 * 60 * 1000),
            "imageBase64" to base64
        )

        // 4) Write to your RTDB URL explicitly
        FirebaseDatabase.getInstance("https://socially-5a61a-default-rtdb.firebaseio.com")
            .getReference("stories")
            .child(uid)
            .child(storyId)
            .setValue(storyNode)
            .addOnSuccessListener {
                Toast.makeText(this, "Story uploaded!", Toast.LENGTH_SHORT).show()
                // Clean up the temp file
                try { tempFile?.delete() } catch (_: Exception) {}
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "DB write failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
