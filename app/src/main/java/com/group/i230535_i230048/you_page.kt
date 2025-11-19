package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

// Data classes matching PHP response structure
data class FollowRequestItem(
    val requestId: String,
    val senderUid: String,
    val createdAt: Long,
    val senderProfile: SenderProfile?
)

data class SenderProfile(
    val uid: String,
    val username: String,
    val fullName: String,
    val avatar: String?,
    val avatarType: String?
)

// Adapter for displaying follow requests
class FollowRequestAdapter(
    private val items: MutableList<FollowRequestItem>,
    private val onAccept: (FollowRequestItem) -> Unit,
    private val onDelete: (FollowRequestItem) -> Unit
) : RecyclerView.Adapter<FollowRequestAdapter.VH>() {

    companion object {
        private const val TAG = "FollowRequestAdapter"
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: ImageView = view.findViewById(R.id.avatar)
        val username: TextView = view.findViewById(R.id.username)
        val subtitle: TextView = view.findViewById(R.id.subtitle)
        val btnAccept: TextView = view.findViewById(R.id.btnAccept)
        val btnDelete: TextView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_request, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val profile = item.senderProfile

        // Display username or fallback to UID
        holder.username.text = profile?.username?.ifBlank { item.senderUid.take(8) } ?: item.senderUid.take(8)
        holder.subtitle.text = "wants to follow you"

        // Load avatar using existing avatar utility
        try {
            if (profile != null) {
                Log.d(TAG, "Loading avatar for user: ${profile.username} (${profile.uid})")
                holder.avatar.loadUserAvatar(profile.uid, profile.uid, R.drawable.default_avatar)
            } else {
                Log.w(TAG, "No profile found for sender: ${item.senderUid}")
                holder.avatar.setImageResource(R.drawable.default_avatar)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading avatar: ${e.message}", e)
            holder.avatar.setImageResource(R.drawable.default_avatar)
        }

        holder.btnAccept.setOnClickListener {
            Log.d(TAG, "Accept clicked for: ${item.senderUid}")
            onAccept(item)
        }

        holder.btnDelete.setOnClickListener {
            Log.d(TAG, "Delete clicked for: ${item.senderUid}")
            onDelete(item)
        }
    }

    override fun getItemCount() = items.size

    fun removeItem(item: FollowRequestItem) {
        val idx = items.indexOfFirst { it.senderUid == item.senderUid }
        if (idx >= 0) {
            Log.d(TAG, "Removing item at index $idx: ${item.senderUid}")
            items.removeAt(idx)
            notifyItemRemoved(idx)
        } else {
            Log.w(TAG, "Item not found for removal: ${item.senderUid}")
        }
    }

    fun setItems(requests: List<FollowRequestItem>) {
        Log.d(TAG, "Setting ${requests.size} items")
        items.clear()
        items.addAll(requests)
        notifyDataSetChanged()
    }
}

class you_page : AppCompatActivity() {

    private lateinit var queue: RequestQueue
    private var meUid: String = ""

    private lateinit var adapter: FollowRequestAdapter
    private lateinit var recyclerView: RecyclerView
    private var noRequestsMessage: TextView? = null  // Made nullable to avoid crash
    private val requestList = mutableListOf<FollowRequestItem>()

    companion object {
        private const val TAG = "you_page"
    }

    fun loadBottomBarAvatar(navProfile: ImageView) {
        try {
            Log.d(TAG, "Loading bottom bar avatar for user: $meUid")
            navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bottom bar avatar: ${e.message}", e)
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "onCreate called")
        Log.d(TAG, "════════════════════════════════")

        try {
            enableEdgeToEdge()
            setContentView(R.layout.activity_you_page)

            queue = Volley.newRequestQueue(this)

            val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
            meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

            Log.d(TAG, "Current user UID: $meUid")

            if (meUid.isEmpty()) {
                Log.e(TAG, "❌ User UID is empty - not logged in")
                Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Load bottom bar avatar safely
            try {
                val navProfile = findViewById<ImageView>(R.id.nav_profile)
                if (navProfile != null) {
                    loadBottomBarAvatar(navProfile)
                } else {
                    Log.w(TAG, "nav_profile not found in layout")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading nav profile: ${e.message}", e)
            }

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Tab navigation - with null checks
            setupNavigation()

            // Setup RecyclerView and empty state message
            setupRecyclerView()

            // Load follow requests
            loadFollowRequests()

        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ CRITICAL ERROR IN onCreate ❌❌❌", e)
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupNavigation() {
        try {
            // Tab navigation
            findViewById<TextView>(R.id.tab_following)?.setOnClickListener {
                Log.d(TAG, "Navigating to following page")
                startActivity(Intent(this, following_page::class.java))
                finish()
            }

            // Bottom navigation
            findViewById<ImageView>(R.id.nav_home)?.setOnClickListener {
                Log.d(TAG, "Navigating to home")
                startActivity(Intent(this, home_page::class.java))
                finish()
            }
            findViewById<ImageView>(R.id.nav_search)?.setOnClickListener {
                Log.d(TAG, "Navigating to search")
                startActivity(Intent(this, search_feed::class.java))
                finish()
            }
            findViewById<ImageView>(R.id.nav_create)?.setOnClickListener {
                Log.d(TAG, "Navigating to create post")
                startActivity(Intent(this, posting::class.java))
                finish()
            }
            findViewById<ImageView>(R.id.nav_profile)?.setOnClickListener {
                Log.d(TAG, "Navigating to my profile")
                startActivity(Intent(this, my_profile::class.java))
                finish()
            }
            Log.d(TAG, "✓ Navigation setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up navigation: ${e.message}", e)
        }
    }

    private fun setupRecyclerView() {
        try {
            Log.d(TAG, "Setting up RecyclerView...")

            // Find RecyclerView (required)
            recyclerView = findViewById(R.id.requestsRecycler)
            Log.d(TAG, "✓ Found requestsRecycler")

            // Try to find empty message view (optional)
            try {
                noRequestsMessage = findViewById(R.id.no_chats_message)
                Log.d(TAG, "✓ Found no_chats_message")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ no_chats_message not found in layout - will only show/hide recycler")
                noRequestsMessage = null
            }

            recyclerView.layoutManager = LinearLayoutManager(this)
            Log.d(TAG, "✓ RecyclerView layout manager set")

            adapter = FollowRequestAdapter(
                requestList,
                onAccept = { item -> respondToRequest(item, "accept") },
                onDelete = { item -> respondToRequest(item, "reject") }
            )
            recyclerView.adapter = adapter
            Log.d(TAG, "✓ Adapter set to RecyclerView")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR in setupRecyclerView: ${e.message}", e)
            throw e  // Re-throw to be caught in onCreate
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - reloading follow requests")
        loadFollowRequests()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
    }

    private fun loadFollowRequests() {
        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "📥 LOADING FOLLOW REQUESTS")
        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "User UID: $meUid")

        val url = AppGlobals.BASE_URL + "follow_requests_list.php?uid=$meUid"
        Log.d(TAG, "API URL: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    Log.d(TAG, "════════════════════════════════")
                    Log.d(TAG, "✅ API RESPONSE RECEIVED")
                    Log.d(TAG, "════════════════════════════════")
                    Log.d(TAG, "Raw response: $response")

                    val json = JSONObject(response)
                    val success = json.getBoolean("success")
                    val message = json.optString("message", "")

                    Log.d(TAG, "Success: $success")
                    Log.d(TAG, "Message: $message")

                    if (success) {
                        val dataObj = json.getJSONObject("data")
                        val requestsArray = dataObj.getJSONArray("requests")
                        val count = dataObj.optInt("count", 0)

                        Log.d(TAG, "────────────────────────────────")
                        Log.d(TAG, "📊 Found $count follow requests")
                        Log.d(TAG, "────────────────────────────────")

                        val requests = mutableListOf<FollowRequestItem>()

                        for (i in 0 until requestsArray.length()) {
                            val reqObj = requestsArray.getJSONObject(i)

                            val requestId = reqObj.getString("requestId")
                            val senderUid = reqObj.getString("senderUid")
                            val createdAt = reqObj.getLong("createdAt")

                            Log.d(TAG, "Request #${i + 1}:")
                            Log.d(TAG, "  - Request ID: $requestId")
                            Log.d(TAG, "  - Sender UID: $senderUid")
                            Log.d(TAG, "  - Created At: $createdAt")

                            val senderProfile = if (reqObj.has("senderProfile") && !reqObj.isNull("senderProfile")) {
                                val profileObj = reqObj.getJSONObject("senderProfile")
                                val profile = SenderProfile(
                                    uid = profileObj.getString("uid"),
                                    username = profileObj.optString("username", ""),
                                    fullName = profileObj.optString("fullName", ""),
                                    avatar = profileObj.optString("avatar", null),
                                    avatarType = profileObj.optString("avatarType", null)
                                )
                                Log.d(TAG, "  - Username: ${profile.username}")
                                Log.d(TAG, "  - Full Name: ${profile.fullName}")
                                profile
                            } else {
                                Log.w(TAG, "  - ⚠️ No sender profile found")
                                null
                            }

                            requests.add(
                                FollowRequestItem(
                                    requestId = requestId,
                                    senderUid = senderUid,
                                    createdAt = createdAt,
                                    senderProfile = senderProfile
                                )
                            )
                        }

                        Log.d(TAG, "────────────────────────────────")
                        Log.d(TAG, "✅ Successfully parsed ${requests.size} requests")
                        Log.d(TAG, "════════════════════════════════")

                        adapter.setItems(requests)
                        updateEmptyState(requests.isEmpty())

                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.w(TAG, "⚠️ API returned error: $errorMsg")
                        updateEmptyState(true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "════════════════════════════════")
                    Log.e(TAG, "❌ PARSING ERROR")
                    Log.e(TAG, "════════════════════════════════")
                    Log.e(TAG, "Error message: ${e.message}")
                    Log.e(TAG, "Response was: $response")
                    Log.e(TAG, "Stack trace:", e)
                    Log.e(TAG, "════════════════════════════════")

                    updateEmptyState(true)
                    Toast.makeText(this, "Error parsing response", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e(TAG, "════════════════════════════════")
                Log.e(TAG, "❌ NETWORK ERROR")
                Log.e(TAG, "════════════════════════════════")
                Log.e(TAG, "Error message: ${error.message}")
                error.networkResponse?.let {
                    Log.e(TAG, "HTTP Status Code: ${it.statusCode}")
                    Log.e(TAG, "Response data: ${String(it.data)}")
                    Log.e(TAG, "Headers: ${it.headers}")
                }
                Log.e(TAG, "Stack trace:", error)
                Log.e(TAG, "════════════════════════════════")

                Toast.makeText(this, "Failed to load requests", Toast.LENGTH_LONG).show()
                updateEmptyState(true)
            }
        )

        Log.d(TAG, "➡️ Adding request to queue")
        queue.add(stringRequest)
    }

    private fun respondToRequest(request: FollowRequestItem, action: String) {
        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "🔄 RESPONDING TO REQUEST")
        Log.d(TAG, "════════════════════════════════")
        Log.d(TAG, "Request ID: ${request.requestId}")
        Log.d(TAG, "Sender UID: ${request.senderUid}")
        Log.d(TAG, "Action: $action")
        Log.d(TAG, "My UID: $meUid")

        val url = AppGlobals.BASE_URL + "follow_request_action.php"
        Log.d(TAG, "API URL: $url")

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    Log.d(TAG, "════════════════════════════════")
                    Log.d(TAG, "✅ ACTION RESPONSE RECEIVED")
                    Log.d(TAG, "════════════════════════════════")
                    Log.d(TAG, "Raw response: $response")

                    val json = JSONObject(response)
                    val success = json.getBoolean("success")
                    val message = json.optString("message", "")

                    Log.d(TAG, "Success: $success")
                    Log.d(TAG, "Message: $message")

                    if (success) {
                        Log.d(TAG, "✅ Request $action successful")

                        // Remove the request from the list
                        adapter.removeItem(request)

                        val displayMessage = if (action == "accept")
                            "Request accepted"
                        else
                            "Request rejected"

                        Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show()

                        // Update empty state if needed
                        updateEmptyState(adapter.itemCount == 0)

                        Log.d(TAG, "Remaining requests: ${adapter.itemCount}")
                        Log.d(TAG, "════════════════════════════════")

                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.e(TAG, "❌ Failed to $action request: $errorMsg")

                        if (json.has("data")) {
                            Log.d(TAG, "Response data: ${json.getJSONObject("data")}")
                        }

                        Toast.makeText(this, "Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "════════════════════════════════")
                    Log.e(TAG, "❌ PARSING ERROR (ACTION)")
                    Log.e(TAG, "════════════════════════════════")
                    Log.e(TAG, "Error message: ${e.message}")
                    Log.e(TAG, "Response was: $response")
                    Log.e(TAG, "Stack trace:", e)
                    Log.e(TAG, "════════════════════════════════")

                    Toast.makeText(this, "Error processing request", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e(TAG, "════════════════════════════════")
                Log.e(TAG, "❌ NETWORK ERROR (ACTION)")
                Log.e(TAG, "════════════════════════════════")
                Log.e(TAG, "Error message: ${error.message}")
                error.networkResponse?.let {
                    Log.e(TAG, "HTTP Status Code: ${it.statusCode}")
                    Log.e(TAG, "Response data: ${String(it.data)}")
                    Log.e(TAG, "Headers: ${it.headers}")
                }
                Log.e(TAG, "Stack trace:", error)
                Log.e(TAG, "════════════════════════════════")

                Toast.makeText(this, "Network error", Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["uid"] = meUid                    // receiver (me)
                params["senderUid"] = request.senderUid  // sender (requester)
                params["action"] = action                // "accept" or "reject"

                Log.d(TAG, "Request params: $params")
                return params
            }
        }

        Log.d(TAG, "➡️ Adding action request to queue")
        queue.add(stringRequest)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        try {
            Log.d(TAG, "────────────────────────────────")
            Log.d(TAG, "Updating UI state: isEmpty=$isEmpty")

            if (isEmpty) {
                recyclerView.visibility = View.GONE
                noRequestsMessage?.visibility = View.VISIBLE
                Log.d(TAG, "📭 Showing empty state message")
            } else {
                recyclerView.visibility = View.VISIBLE
                noRequestsMessage?.visibility = View.GONE
                Log.d(TAG, "📬 Showing recycler with ${adapter.itemCount} items")
            }

            Log.d(TAG, "────────────────────────────────")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating empty state: ${e.message}", e)
        }
    }
}