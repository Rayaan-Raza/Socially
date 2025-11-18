package com.group.i230535_i230048

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
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
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

// Adapter for displaying follow requests
class FollowRequestAdapter(
    private val items: MutableList<FollowRequestItem>,
    private val onAccept: (FollowRequestItem) -> Unit,
    private val onDelete: (FollowRequestItem) -> Unit
) : RecyclerView.Adapter<FollowRequestAdapter.VH>() {

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

        holder.username.text = profile?.username?.ifBlank { item.senderUid.take(8) } ?: item.senderUid.take(8)
        holder.subtitle.text = "wants to follow you"

        // Load avatar using existing avatar utility
        try {
            if (profile != null) {
                holder.avatar.loadUserAvatar(profile.uid, profile.uid, R.drawable.default_avatar)
            } else {
                holder.avatar.setImageResource(R.drawable.default_avatar)
            }
        } catch (e: Exception) {
            Log.e("FollowRequestAdapter", "Error loading avatar: ${e.message}")
            holder.avatar.setImageResource(R.drawable.default_avatar)
        }

        holder.btnAccept.setOnClickListener { onAccept(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun removeItem(item: FollowRequestItem) {
        val idx = items.indexOfFirst { it.senderUid == item.senderUid }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun setItems(requests: List<FollowRequestItem>) {
        items.clear()
        items.addAll(requests)
        notifyDataSetChanged()
    }
}

// Data class matching the PHP response structure
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

class you_page : AppCompatActivity() {

    private lateinit var queue: RequestQueue
    private var meUid: String = ""

    private lateinit var adapter: FollowRequestAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyStateText: TextView
    private val requestList = mutableListOf<FollowRequestItem>()

    fun loadBottomBarAvatar(navProfile: ImageView) {
        try {
            navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
        } catch (e: Exception) {
            Log.e("you_page", "Error loading avatar: ${e.message}")
            navProfile.setImageResource(R.drawable.oval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_you_page)

        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""

        if (meUid.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val navProfile = findViewById<ImageView>(R.id.nav_profile)
        loadBottomBarAvatar(navProfile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Tabs + nav
        findViewById<TextView>(R.id.tab_following).setOnClickListener {
            startActivity(Intent(this, following_page::class.java))
            finish()
        }

        // Recycler setup
        recycler = findViewById(R.id.requestsRecycler)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = FollowRequestAdapter(requestList,
            onAccept = { item -> respondToRequest(item, "accept") },
            onDelete = { item -> respondToRequest(item, "reject") }
        )
        recycler.adapter = adapter

        // Empty state text
        emptyStateText = findViewById(R.id.emptyStateText)

        // Load follow requests
        loadFollowRequests()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun loadFollowRequests() {
        Log.d("you_page", "Loading follow requests for uid: $meUid")

        // Use existing follow_requests_list.php endpoint
        val url = AppGlobals.BASE_URL + "follow_requests_list.php?uid=$meUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    Log.d("you_page", "Raw response: $response")
                    val json = JSONObject(response)

                    if (json.getBoolean("success")) {
                        val dataObj = json.getJSONObject("data")
                        val requestsArray = dataObj.getJSONArray("requests")

                        Log.d("you_page", "Found ${requestsArray.length()} follow requests")

                        val requests = mutableListOf<FollowRequestItem>()

                        for (i in 0 until requestsArray.length()) {
                            val reqObj = requestsArray.getJSONObject(i)

                            val senderProfile = if (reqObj.has("senderProfile") && !reqObj.isNull("senderProfile")) {
                                val profileObj = reqObj.getJSONObject("senderProfile")
                                SenderProfile(
                                    uid = profileObj.getString("uid"),
                                    username = profileObj.optString("username", ""),
                                    fullName = profileObj.optString("fullName", ""),
                                    avatar = profileObj.optString("avatar", null),
                                    avatarType = profileObj.optString("avatarType", null)
                                )
                            } else {
                                null
                            }

                            requests.add(
                                FollowRequestItem(
                                    requestId = reqObj.getString("requestId"),
                                    senderUid = reqObj.getString("senderUid"),
                                    createdAt = reqObj.getLong("createdAt"),
                                    senderProfile = senderProfile
                                )
                            )
                        }

                        adapter.setItems(requests)
                        updateEmptyState(requests.isEmpty())

                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.w("you_page", "API error: $errorMsg")
                        updateEmptyState(true)
                    }
                } catch (e: Exception) {
                    Log.e("you_page", "Error parsing requests: ${e.message}")
                    Log.e("you_page", "Response was: $response")
                    Log.e("you_page", "Stack trace: ${e.stackTraceToString()}")
                    updateEmptyState(true)
                }
            },
            { error ->
                Log.e("you_page", "Volley error fetching requests: ${error.message}")
                error.networkResponse?.let {
                    Log.e("you_page", "Network response code: ${it.statusCode}")
                    Log.e("you_page", "Network response: ${String(it.data)}")
                }
                Toast.makeText(this, "Failed to load requests", Toast.LENGTH_SHORT).show()
                updateEmptyState(true)
            }
        )
        queue.add(stringRequest)
    }

    private fun respondToRequest(request: FollowRequestItem, action: String) {
        Log.d("you_page", "Responding to request - senderUid: ${request.senderUid}, action: $action")

        // Use existing follow_request_action.php endpoint
        val url = AppGlobals.BASE_URL + "follow_request_action.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    Log.d("you_page", "Response from action: $response")
                    val json = JSONObject(response)

                    if (json.getBoolean("success")) {
                        // Remove the request from the list
                        adapter.removeItem(request)

                        val message = if (action == "accept") "Request accepted" else "Request rejected"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                        // Update empty state if needed
                        updateEmptyState(adapter.itemCount == 0)

                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.e("you_page", "Failed to $action request: $errorMsg")
                        Toast.makeText(this, "Failed: $errorMsg", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("you_page", "Error parsing response: ${e.message}")
                    Log.e("you_page", "Response was: $response")
                    Log.e("you_page", "Stack trace: ${e.stackTraceToString()}")
                    Toast.makeText(this, "Error processing request", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("you_page", "Volley error responding: ${error.message}")
                error.networkResponse?.let {
                    Log.e("you_page", "Network response code: ${it.statusCode}")
                    Log.e("you_page", "Network response: ${String(it.data)}")
                }
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                // follow_request_action.php expects: uid, senderUid, action
                params["uid"] = meUid           // receiver (me)
                params["senderUid"] = request.senderUid  // sender (requester)
                params["action"] = action       // "accept" or "reject"

                Log.d("you_page", "Sending params: uid=$meUid, senderUid=${request.senderUid}, action=$action")
                return params
            }
        }
        queue.add(stringRequest)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyStateText.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            emptyStateText.text = "No pending follow requests"
        } else {
            emptyStateText.visibility = View.GONE
            recycler.visibility = View.VISIBLE
        }
    }
}