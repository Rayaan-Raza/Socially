package com.group.i230535_i230048// Or your app's main package

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.group.i230535_i230048.AppGlobals
import com.group.i230535_i230048.AppDbHelper
import com.group.i230535_i230048.DB
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val dbHelper = AppDbHelper(appContext)
    private val queue = Volley.newRequestQueue(appContext)

    companion object {
        const val WORK_NAME = "OfflineSyncJob"
    }

    /**
     * This is the main function that WorkManager will run in the background.
     */
    override suspend fun doWork(): Result = coroutineScope {
        val pendingActions = getPendingActions()

        if (pendingActions.isEmpty()) {
            Log.d("SyncWorker", "No pending actions to sync.")
            return@coroutineScope Result.success()
        }

        Log.d("SyncWorker", "Found ${pendingActions.size} actions to sync.")
        var allSucceeded = true

        for (action in pendingActions) {
            val (id, endpoint, payload) = action
            val success = when (endpoint) {
                "create_post.php" -> syncAction(endpoint, payload)
                "like_post.php" -> syncAction(endpoint, payload)
                "add_comment.php" -> syncAction(endpoint, payload)
                "send_message.php" -> syncAction(endpoint, payload)
                "upload_story.php" -> syncAction(endpoint, payload)
                "edit_message.php" -> syncAction(endpoint, payload)
                "delete_message.php" -> syncAction(endpoint, payload)
                "report_screenshot.php" -> syncAction(endpoint, payload)
                // Add other endpoints here
                else -> {
                    Log.w("SyncWorker", "Unknown endpoint: $endpoint")
                    false // Mark as failed
                }
            }

            if (success) {
                // If the upload was successful, mark it as "SENT" in the DB
                markActionAsSent(id)
            } else {
                // If any action fails, we'll retry the whole job later
                allSucceeded = false
            }
        }

        return@coroutineScope if (allSucceeded) Result.success() else Result.retry()
    }

    /**
     * A generic function to make a Volley POST request.
     */
    private suspend fun syncAction(endpoint: String, payload: String): Boolean = suspendCoroutine { continuation ->
        val url = AppGlobals.BASE_URL + endpoint
        Log.d("SyncWorker", "Syncing to: $url")

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        Log.d("SyncWorker", "Sync success for $endpoint")
                        continuation.resume(true)
                    } else {
                        Log.w("SyncWorker", "Sync API error for $endpoint: ${json.getString("message")}")
                        continuation.resume(false)
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Sync parse error for $endpoint: ${e.message}")
                    continuation.resume(false)
                }
            },
            { error ->
                Log.e("SyncWorker", "Sync network error for $endpoint: ${error.message}")
                continuation.resume(false) // Network error, will retry
            }) {

            override fun getParams(): MutableMap<String, String> {
                // Convert the saved JSON payload back into Volley's params map
                val params = HashMap<String, String>()
                try {
                    val jsonObject = JSONObject(payload)
                    jsonObject.keys().forEach { key ->
                        params[key] = jsonObject.getString(key)
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to parse payload for $endpoint", e)
                }
                return params
            }
        }
        queue.add(stringRequest)
    }

    /**
     * Reads the SQLite database for any actions still marked "PENDING".
     */
    private fun getPendingActions(): List<Triple<Int, String, String>> {
        val actions = mutableListOf<Triple<Int, String, String>>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.SyncQueue.TABLE_NAME,
            arrayOf(DB.SyncQueue.COLUMN_ID, DB.SyncQueue.COLUMN_ENDPOINT, DB.SyncQueue.COLUMN_PAYLOAD),
            "${DB.SyncQueue.COLUMN_STATUS} = ?",
            arrayOf("PENDING"),
            null, null, DB.SyncQueue.COLUMN_ID + " ASC"
        )
        while (cursor.moveToNext()) {
            actions.add(
                Triple(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DB.SyncQueue.COLUMN_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(DB.SyncQueue.COLUMN_ENDPOINT)),
                    cursor.getString(cursor.getColumnIndexOrThrow(DB.SyncQueue.COLUMN_PAYLOAD))
                )
            )
        }
        cursor.close()
        return actions
    }

    /**
     * Updates an action's status to "SENT" so we don't send it again.
     */
    private fun markActionAsSent(id: Int) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues()
            values.put(DB.SyncQueue.COLUMN_STATUS, "SENT")
            db.update(
                DB.SyncQueue.TABLE_NAME,
                values,
                "${DB.SyncQueue.COLUMN_ID} = ?",
                arrayOf(id.toString())
            )
            Log.d("SyncWorker", "Marked action $id as SENT")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error marking action $id as SENT", e)
        }
    }
}