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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
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

// REMOVED: SimpleUser data class (using full User model now)

class FollowListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var queue: RequestQueue // CHANGED

    private val list = mutableListOf<User>() // CHANGED: Now a list of full User
    private lateinit var adapter: FollowUserAdapter

    private var mode: String = "followers"
    private var targetUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        queue = Volley.newRequestQueue(this) // CHANGED

        mode = intent.getStringExtra("mode") ?: "followers"
        targetUid = intent.getStringExtra("uid") ?: ""

        findViewById<TextView>(R.id.title).text =
            if (mode == "followers") "Followers" else "Following"

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        recycler = findViewById(R.id.recycler)
        emptyText = findViewById(R.id.emptyText)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        // CHANGED: Adapter now takes List<User>
        adapter = FollowUserAdapter(list) { clicked ->
            startActivity(Intent(this, view_profile::class.java).apply {
                putExtra("userId", clicked.uid)
            })
        }
        recycler.adapter = adapter

        loadListFromApi()
    }

    // --- CHANGED: Replaced Firebase logic with a single API call ---
    private fun loadListFromApi() {
        if (targetUid.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "User not found."
            return
        }

        val endpoint = if (mode == "followers") "get_followers.php" else "get_following.php"
        val url = AppGlobals.BASE_URL + "$endpoint?user_id=$targetUid" // (from ApiService.kt)

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        val listType = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = Gson().fromJson(dataArray.toString(), listType)

                        list.clear()
                        list.addAll(users)
                        adapter.notifyDataSetChanged()

                        if (list.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                            emptyText.text = if (mode == "followers") "No followers yet." else "Not following anyone."
                        } else {
                            emptyText.visibility = View.GONE
                        }
                    } else {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = json.getString("message")
                    }
                } catch (e: Exception) {
                    Log.e("FollowList", "Error parsing list: ${e.message}")
                    emptyText.text = "Error loading list."
                }
            },
            { error ->
                Log.e("FollowList", "Volley error: ${error.message}")
                emptyText.text = "Network error."
            }
        )
        queue.add(stringRequest)
    }
    // REMOVED: loadList() and fetchUsers()
}

// --- CHANGED: Adapter now uses the full User model ---
class FollowUserAdapter(
    private val data: List<User>,
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<FollowUserAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.avatar)
        val username: TextView = v.findViewById(R.id.username)
        val subtitle: TextView = v.findViewById(R.id.subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_follow_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val u = data[position]
        h.username.text = u.username
        h.subtitle.text = u.fullName

        // CHANGED: Use our standard loadUserAvatar function
        h.avatar.loadUserAvatar(u.uid, u.uid, R.drawable.default_avatar)

        // REMOVED: Old complex Glide logic

        h.itemView.setOnClickListener { onClick(u) }
    }

    override fun getItemCount() = data.size
}