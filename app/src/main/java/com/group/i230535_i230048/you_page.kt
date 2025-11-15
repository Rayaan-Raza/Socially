package com.group.i230535_i230048

import android.content.Context // CHANGED
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log // CHANGED
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast // CHANGED
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
import com.bumptech.glide.Glide
// REMOVED: Firebase imports
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import org.json.JSONObject // CHANGED

// REMOVED: FollowRequest data class

// --- CHANGED: Adapter now uses the full User model ---
class FollowRequestAdapter(
    private val items: MutableList<User>,
    private val onAccept: (User) -> Unit,
    private val onDelete: (User) -> Unit
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
        holder.username.text = item.username.ifBlank { item.uid.take(8) }
        holder.subtitle.text = "wants to follow you"

        // CHANGED: Use our standard loadUserAvatar function
        holder.avatar.loadUserAvatar(item.uid, item.uid, R.drawable.default_avatar)

        holder.btnAccept.setOnClickListener { onAccept(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun removeItem(item: User) {
        val idx = items.indexOfFirst { it.uid == item.uid }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun setItems(users: List<User>) {
        items.clear()
        items.addAll(users)
        notifyDataSetChanged()
    }
}

class you_page : AppCompatActivity() {

    // --- CHANGED: Swapped to Volley/DB/Session ---
    private lateinit var queue: RequestQueue
    private var meUid: String = ""
    // ---

    private lateinit var adapter: FollowRequestAdapter
    private lateinit var recycler: RecyclerView
    private val requestList = mutableListOf<User>()

    // REMOVED: Listener refs

    // CHANGED: Migrated to load from local DB
    fun loadBottomBarAvatar(navProfile: ImageView) {
        navProfile.loadUserAvatar(meUid, meUid, R.drawable.oval)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_you_page)

        // --- CHANGED: Setup DB, Volley, and Session ---
        queue = Volley.newRequestQueue(this)

        val prefs = getSharedPreferences(AppGlobals.PREFS_NAME, Context.MODE_PRIVATE)
        meUid = prefs.getString(AppGlobals.KEY_USER_UID, "") ?: ""
        // ---

        if (meUid.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish(); return
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
            startActivity(Intent(this, following_page::class.java)); finish()
        }
        // ... other nav clicks ...

        // Recycler setup
        recycler = findViewById(R.id.requestsRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FollowRequestAdapter(requestList,
            onAccept = { item -> respondToRequest(item, "accept") },
            onDelete = { item -> respondToRequest(item, "reject") }
        )
        recycler.adapter = adapter

        // CHANGED: Load from API
        attachRequestsListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        // REMOVED: listener removal
    }

    // --- CHANGED: Migrated to fetch from API ---
    private fun attachRequestsListener() {
        // TODO: Dev A needs to create this API endpoint
        val url = AppGlobals.BASE_URL + "get_follow_requests.php?uid=$meUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")
                        val listType = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = Gson().fromJson(dataArray.toString(), listType)

                        adapter.setItems(users) // Update adapter
                        // TODO: Show/hide empty state TextView
                    } else {
                        Log.w("you_page", "API error: ${json.getString("message")}")
                    }
                } catch (e: Exception) { Log.e("you_page", "Error parsing requests: ${e.message}") }
            },
            { error -> Log.e("you_page", "Volley error fetching requests: ${error.message}") }
        )
        queue.add(stringRequest)
    }

    // REMOVED: fetchRequester()

    // --- CHANGED: Migrated to one function that calls API ---
    private fun respondToRequest(requester: User, action: String) { // action is "accept" or "reject"
        // TODO: Dev A needs to create this API endpoint
        val url = AppGlobals.BASE_URL + "respond_follow_request.php"

        val stringRequest = object : StringRequest(Request.Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        // On success, remove the item from the list
                        adapter.removeItem(requester)
                        Toast.makeText(this, "Request $action" + "ed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed: ${json.getString("message")}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Log.e("you_page", "Error parsing response: ${e.message}") }
            },
            { error ->
                Log.e("you_page", "Volley error responding: ${error.message}")
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["requester_id"] = requester.uid
                params["my_id"] = meUid
                params["action"] = action // "accept" or "reject"
                return params
            }
        }
        queue.add(stringRequest)
    }

    // REMOVED: acceptRequest() and declineRequest()
}