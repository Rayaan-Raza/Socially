package com.group.i230535_i230048

import android.content.ContentValues
import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object AvatarUtils {
    private val cache = ConcurrentHashMap<String, String?>()

    fun getProfileUrlFromDb(context: Context, uid: String): String? {
        if (cache.containsKey(uid)) return cache[uid]

        val dbHelper = AppDbHelper(context)
        var url: String? = null
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                DB.User.TABLE_NAME,
                arrayOf(DB.User.COLUMN_PROFILE_PIC_URL, DB.User.COLUMN_PHOTO),
                "${DB.User.COLUMN_UID} = ?",
                arrayOf(uid),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                url = cursor.getString(0) // Try URL
                if (url.isNullOrBlank()) {
                    url = cursor.getString(1) // Try Photo (Base64)
                }
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("AvatarUtils", "DB Read Error", e)
        }
        cache[uid] = url
        return url
    }

    fun saveUrlToDb(context: Context, uid: String, url: String) {
        cache[uid] = url
        Thread {
            try {
                val dbHelper = AppDbHelper(context)
                val db = dbHelper.writableDatabase
                val cv = ContentValues()

                if (url.startsWith("http")) {
                    cv.put(DB.User.COLUMN_PROFILE_PIC_URL, url)
                } else {
                    cv.put(DB.User.COLUMN_PHOTO, url)
                }

                val rows = db.update(DB.User.TABLE_NAME, cv, "${DB.User.COLUMN_UID} = ?", arrayOf(uid))
                if (rows == 0) {
                    cv.put(DB.User.COLUMN_UID, uid)
                    cv.put(DB.User.COLUMN_USERNAME, "User")
                    db.insert(DB.User.TABLE_NAME, null, cv)
                }
            } catch (e: Exception) {
                Log.e("AvatarUtils", "DB Write Error", e)
            }
        }.start()
    }
}

// --- FIXED LOADING LOGIC BASED ON OLD CODE ---
fun ImageView.loadAvatarFromString(maybeBase64OrUrl: String?, placeholderRes: Int) {
    if (maybeBase64OrUrl.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }

    val s = maybeBase64OrUrl.trim()

    // Logic from your old code: Check if it's NOT a http URL
    if (!s.startsWith("http", ignoreCase = true)) {
        try {
            // 1. CLEAN IT (The "Comma" fix from your old home_page.kt)
            val clean = s.substringAfter("base64,", s)

            // 2. DECODE TO BYTES (The Byte Array fix)
            val bytes = Base64.decode(clean, Base64.DEFAULT)

            // 3. LOAD BYTES WITH GLIDE
            Glide.with(this.context)
                .asBitmap()
                .load(bytes)
                .circleCrop()
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(this)
        } catch (e: Exception) {
            Log.e("AvatarUtils", "Base64 decode failed", e)
            setImageResource(placeholderRes)
        }
    } else {
        // It is a standard URL
        Glide.with(this.context)
            .load(s)
            .circleCrop()
            .placeholder(placeholderRes)
            .error(placeholderRes)
            .into(this)
    }
}

fun ImageView.loadUserAvatar(uid: String, fallbackUid: String, placeholderRes: Int) {
    val context = this.context

    // 1. Load DB immediately (Fast)
    val localUrl = AvatarUtils.getProfileUrlFromDb(context, uid)
        ?: AvatarUtils.getProfileUrlFromDb(context, fallbackUid)

    loadAvatarFromString(localUrl, placeholderRes)

    // 2. Fetch Fresh from API
    val url = "${AppGlobals.BASE_URL}user_basic_get.php?uid=$uid"
    val stringRequest = StringRequest(Request.Method.GET, url,
        { response ->
            try {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val data = json.getJSONObject("data")
                    val serverAvatar = data.optString("profileUrl", "")
                        .takeIf { it.isNotEmpty() }
                        ?: data.optString("avatar", "")

                    if (serverAvatar.isNotEmpty() && serverAvatar != localUrl) {
                        loadAvatarFromString(serverAvatar, placeholderRes)
                        AvatarUtils.saveUrlToDb(context, uid, serverAvatar)
                    }
                }
            } catch (_: Exception) { }
        },
        { }
    )
    Volley.newRequestQueue(context.applicationContext).add(stringRequest)
}