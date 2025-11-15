package com.group.i230535_i230048

import android.content.Context
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.group.i230535_i230048.AppDbHelper // Make sure this import is used
import com.group.i230535_i230048.DB // Make sure this import is used
import java.util.concurrent.ConcurrentHashMap

object AvatarUtils {

    // Cache still helps reduce DB reads
    private val cache = ConcurrentHashMap<String, String?>()

    // NEW: Reads from SQLite, not Firebase
    fun getProfileUrlFromDb(context: Context, uid: String): String? {
        // 1. Check cache first
        cache[uid]?.let { return it }

        // 2. If not in cache, query local DB
        val dbHelper = AppDbHelper(context)
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DB.User.TABLE_NAME,
            arrayOf(DB.User.COLUMN_PROFILE_PIC_URL),
            "${DB.User.COLUMN_UID} = ?",
            arrayOf(uid),
            null, null, null
        )

        var url: String? = null
        if (cursor.moveToFirst()) {
            url = cursor.getString(cursor.getColumnIndexOrThrow(DB.User.COLUMN_PROFILE_PIC_URL))
        }
        cursor.close()

        // 3. Save to cache and return
        cache[uid] = url
        return url
    }
}

// --- MOVED THIS FUNCTION ---
// This function is now at the top level (OUTSIDE the AvatarUtils object)
fun ImageView.loadAvatarFromString(maybeBase64OrUrl: String?, placeholderRes: Int) {
    if (maybeBase64OrUrl.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }
    val s = maybeBase64OrUrl.trim()
    val isProbablyBase64 = s.startsWith("data:image") || s.length > 300 || !s.startsWith("http")

    if (isProbablyBase64) {
        val clean = s.substringAfter(",", s)
        val bytes = try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { null }
        if (bytes != null) {
            Glide.with(this.context)
                .asBitmap()
                .load(bytes)
                .circleCrop()
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(this)
        } else {
            setImageResource(placeholderRes)
        }
    } else {
        Glide.with(this.context)
            .load(s)
            .circleCrop()
            .placeholder(placeholderRes)
            .error(placeholderRes)
            .into(this)
    }
}

// This function can now see "loadAvatarFromString" because they are both at the top level
fun ImageView.loadUserAvatar(uid: String, fallbackUid: String, placeholderRes: Int) {
    // Try to load primary user's URL from our local DB
    val primaryUrl = AvatarUtils.getProfileUrlFromDb(this.context, uid)

    if (!primaryUrl.isNullOrBlank()) {
        this.loadAvatarFromString(primaryUrl, placeholderRes)
    } else {
        // If primary fails, try to load fallback user's URL from our local DB
        val fallbackUrl = AvatarUtils.getProfileUrlFromDb(this.context, fallbackUid)
        this.loadAvatarFromString(fallbackUrl, placeholderRes)
    }
}