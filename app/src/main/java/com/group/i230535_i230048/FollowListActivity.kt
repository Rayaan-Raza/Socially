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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class FollowListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var queue: RequestQueue

    private val list = mutableListOf<User>()
    private lateinit var adapter: FollowUserAdapter

    private var mode: String = "followers"
    private var targetUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

        queue = Volley.newRequestQueue(this)

        mode = intent.getStringExtra("mode") ?: "followers"
        targetUid = intent.getStringExtra("uid") ?: ""

        findViewById<TextView>(R.id.title).text =
            if (mode == "followers") "Followers" else "Following"

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        recycler = findViewById(R.id.recycler)
        emptyText = findViewById(R.id.emptyText)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        adapter = FollowUserAdapter(list) { clicked ->
            startActivity(Intent(this, view_profile::class.java).apply {
                putExtra("userId", clicked.uid)
            })
        }
        recycler.adapter = adapter

        loadListFromApi()
    }

    private fun loadListFromApi() {
        if (targetUid.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "User not found."
            return
        }

        // API Spec: followers_list.php?uid=user_uid OR following_list.php?uid=user_uid
        val endpoint = if (mode == "followers") "followers_list.php" else "following_list.php"
        val url = AppGlobals.BASE_URL + "$endpoint?uid=$targetUid"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        list.clear()
                        for (i in 0 until dataArray.length()) {
                            val userObj = dataArray.getJSONObject(i)
                            list.add(
                                User(
                                    uid = userObj.getString("uid"),
                                    username = userObj.getString("username"),
                                    fullName = userObj.optString("fullName", ""),
                                    bio = userObj.optString("bio", ""),
                                    profilePictureUrl = userObj.optString("profilePictureUrl", ""),
                                    photo = userObj.optString("photo", ""),
                                    followersCount = userObj.optInt("followersCount", 0),
                                    followingCount = userObj.optInt("followingCount", 0),
                                    postsCount = userObj.optInt("postsCount", 0)
                                )
                            )
                        }

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
}

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

        h.avatar.loadUserAvatar(u.uid, u.uid, R.drawable.default_avatar)

        h.itemView.setOnClickListener { onClick(u) }
    }

    override fun getItemCount() = data.size
}