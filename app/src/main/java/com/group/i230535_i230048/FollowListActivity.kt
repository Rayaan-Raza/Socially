package com.group.i230535_i230048

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*


data class SimpleUser(
    val uid: String = "",
    val username: String = "",
    val fullName: String = "",
    val profilePictureUrl: String? = null
)

class FollowListActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView

    private val rtdb: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val list = mutableListOf<SimpleUser>()
    private lateinit var adapter: FollowUserAdapter

    private var mode: String = "followers"   // or "following"
    private var targetUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_follow_list)

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
            // open profile
            startActivity(Intent(this, view_profile::class.java).apply {
                putExtra("userId", clicked.uid)
            })
        }
        recycler.adapter = adapter

        loadList()
    }

    private fun loadList() {
        if (targetUid.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "User not found."
            return
        }

        val edgeRef: DatabaseReference = if (mode == "followers") {
            rtdb.getReference("followers").child(targetUid)
        } else {
            rtdb.getReference("following").child(targetUid)
        }

        edgeRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uids = snapshot.children.mapNotNull { it.key }
                if (uids.isEmpty()) {
                    list.clear()
                    adapter.notifyDataSetChanged()
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = if (mode == "followers") "No followers yet." else "Not following anyone."
                    return
                }

                // fetch user profiles for these uids
                fetchUsers(uids)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchUsers(uids: List<String>) {
        // read /users/<uid> for each; small lists fine; for very large lists you’d paginate
        list.clear()
        val usersRef = rtdb.getReference("users")
        val pending = uids.toMutableSet()

        uids.forEach { id ->
            usersRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val username = s.child("username").getValue(String::class.java) ?: ""
                    val fullName = s.child("fullName").getValue(String::class.java) ?: ""
                    val profile = s.child("profilePictureUrl").getValue(String::class.java)
                        ?: s.child("profileImageUrl").getValue(String::class.java)

                    list.add(SimpleUser(id, username, fullName, profile))
                    pending.remove(id)
                    if (pending.isEmpty()) {
                        // sort by username for determinism
                        list.sortBy { it.username.lowercase() }
                        adapter.notifyDataSetChanged()
                        emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    pending.remove(id)
                }
            })
        }
    }
}

class FollowUserAdapter(
    private val data: List<SimpleUser>,
    private val onClick: (SimpleUser) -> Unit
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
        h.username.text = if (u.username.isNotBlank()) u.username else u.fullName.ifBlank { u.uid.takeLast(6) }
        h.subtitle.text = u.fullName

        // Load avatar: supports http URL or base64
        val pic = u.profilePictureUrl
        if (!pic.isNullOrBlank()) {
            if (pic.startsWith("http", true)) {
                Glide.with(h.avatar.context)
                    .load(pic)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(h.avatar)
            } else {
                // assume base64
                try {
                    val clean = pic.substringAfter("base64,", pic)
                    val bytes = Base64.decode(clean, Base64.DEFAULT)
                    Glide.with(h.avatar.context)
                        .asBitmap()
                        .load(bytes)
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .circleCrop()
                        .into(h.avatar)
                } catch (_: Exception) {
                    h.avatar.setImageResource(R.drawable.default_avatar)
                }
            }
        } else {
            h.avatar.setImageResource(R.drawable.default_avatar)
        }

        h.itemView.setOnClickListener { onClick(u) }
    }

    override fun getItemCount() = data.size
}
