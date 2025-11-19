package com.group.i230535_i230048

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject


fun loadUserProfile(
    context: Context,
    userId: String,
    onSuccess: (UserProfileData) -> Unit,
    onError: (() -> Unit)? = null
) {
    val url = AppGlobals.BASE_URL + "getUserProfile.php?uid=$userId"
    val queue = Volley.newRequestQueue(context)

    val stringRequest = StringRequest(
        Request.Method.GET, url,
        { response ->
            try {
                Log.d("loadUserProfile", "Response for $userId: $response")
                val json = JSONObject(response)
                
                if (json.getBoolean("success")) {
                    val userObj = json.getJSONObject("data")
                    
                    val profileData = UserProfileData(
                        uid = userObj.getString("uid"),
                        username = userObj.optString("username", ""),
                        fullName = userObj.optString("fullName", ""),
                        email = userObj.optString("email", ""),
                        bio = userObj.optString("bio", ""),
                        profilePictureUrl = userObj.optString("profilePictureUrl", ""),
                        followersCount = userObj.optInt("followersCount", 0),
                        followingCount = userObj.optInt("followingCount", 0),
                        postsCount = userObj.optInt("postsCount", 0),
                        isPrivate = userObj.optInt("isPrivate", 0) == 1
                    )
                    
                    Log.d("loadUserProfile", "✓ Loaded profile: ${profileData.username}")
                    onSuccess(profileData)
                } else {
                    val message = json.optString("message", "Unknown error")
                    Log.w("loadUserProfile", "API error: $message")
                    onError?.invoke()
                }
            } catch (e: Exception) {
                Log.e("loadUserProfile", "Parse error: ${e.message}", e)
                onError?.invoke()
            }
        },
        { error ->
            Log.e("loadUserProfile", "Network error: ${error.message}", error)
            onError?.invoke()
        }
    )
    
    queue.add(stringRequest)
}

data class UserProfileData(
    val uid: String,
    val username: String,
    val fullName: String,
    val email: String,
    val bio: String,
    val profilePictureUrl: String,
    val followersCount: Int,
    val followingCount: Int,
    val postsCount: Int,
    val isPrivate: Boolean
)

object UserProfileCache {
    private val cache = mutableMapOf<String, UserProfileData>()
    private val cacheTTL = 5 * 60 * 1000L // 5 minutes
    private val cacheTimestamps = mutableMapOf<String, Long>()
    
    fun get(userId: String): UserProfileData? {
        val timestamp = cacheTimestamps[userId] ?: return null
        if (System.currentTimeMillis() - timestamp > cacheTTL) {
            cache.remove(userId)
            cacheTimestamps.remove(userId)
            return null
        }
        return cache[userId]
    }
    
    fun put(userId: String, data: UserProfileData) {
        cache[userId] = data
        cacheTimestamps[userId] = System.currentTimeMillis()
    }
    
    fun clear(userId: String? = null) {
        if (userId != null) {
            cache.remove(userId)
            cacheTimestamps.remove(userId)
        } else {
            cache.clear()
            cacheTimestamps.clear()
        }
    }
}

fun loadUserProfileCached(
    context: Context,
    userId: String,
    onSuccess: (UserProfileData) -> Unit,
    onError: (() -> Unit)? = null
) {
    UserProfileCache.get(userId)?.let {
        Log.d("loadUserProfile", "✓ Using cached profile for: $userId")
        onSuccess(it)
        return
    }
    
    loadUserProfile(
        context = context,
        userId = userId,
        onSuccess = { profile ->
            UserProfileCache.put(userId, profile)
            onSuccess(profile)
        },
        onError = onError
    )
}

fun clearProfileCache(userId: String) {
    UserProfileCache.clear(userId)
}
