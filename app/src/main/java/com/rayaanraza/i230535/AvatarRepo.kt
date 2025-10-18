package com.rayaanraza.i230535

import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.ConcurrentHashMap

object AvatarRepo {
    // Cache users' profilePictureUrl (can be Base64 or URL; null = not set)
    private val cache = ConcurrentHashMap<String, String?>()
    private val db = FirebaseDatabase.getInstance().reference

    fun getProfileB64OrUrl(uid: String, onResult: (String?) -> Unit) {
        cache[uid]?.let { onResult(it); return }

        db.child("users").child(uid).child("profilePictureUrl")
            .get()
            .addOnSuccessListener { snap ->
                val value = snap.getValue(String::class.java)
                android.util.Log.d("AvatarRepo", "uid=$uid dpLen=${value?.length ?: 0}")
                cache[uid] = value
                onResult(value)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AvatarRepo", "uid=$uid read failed: ${e.message}")
                cache[uid] = null
                onResult(null)
            }
    }
}

/** Load Base64/URL into ImageView with circleCrop */
fun ImageView.loadAvatarFromString(maybeBase64OrUrl: String?, placeholderRes: Int) {
    if (maybeBase64OrUrl.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }
    val s = maybeBase64OrUrl.trim()
    val isProbablyBase64 = s.startsWith("data:image") || s.length > 300 || !s.startsWith("http")

    if (isProbablyBase64) {
        val clean = s.substringAfter(",", s) // strip any "data:image/...;base64,"
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

/** Load avatar for uid, fallback to fallbackUid (usually current user) */
fun ImageView.loadUserAvatar(uid: String, fallbackUid: String, placeholderRes: Int) {
    AvatarRepo.getProfileB64OrUrl(uid) { primary ->
        if (primary != null) {
            this.loadAvatarFromString(primary, placeholderRes)
        } else {
            // try fallback (current user's DP)
            AvatarRepo.getProfileB64OrUrl(fallbackUid) { fb ->
                this.loadAvatarFromString(fb, placeholderRes)
            }
        }
    }
}
