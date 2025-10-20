package com.group.i230535_i230048

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class FollowRequest(
    val requesterUid: String = "",
    val username: String = "",
    val profilePictureUrl: String? = null
)

class FollowRequestAdapter(
    private val items: MutableList<FollowRequest>,
    private val onAccept: (FollowRequest) -> Unit,
    private val onDelete: (FollowRequest) -> Unit
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
        holder.username.text = item.username.ifBlank { item.requesterUid.take(8) }
        holder.subtitle.text = "wants to follow you"

        // Load avatar: supports data:image/...;base64, and raw base64 or URL
        val pic = item.profilePictureUrl
        if (!pic.isNullOrBlank()) {
            val clean = pic.substringAfter(",", pic) // strip data prefix if present
            val bytes = try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { null }
            if (bytes != null) {
                Glide.with(holder.avatar.context)
                    .asBitmap()
                    .load(bytes)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(holder.avatar)
            } else {
                Glide.with(holder.avatar.context)
                    .load(pic)
                    .placeholder(R.drawable.default_avatar)
                    .error(R.drawable.default_avatar)
                    .circleCrop()
                    .into(holder.avatar)
            }
        } else {
            holder.avatar.setImageResource(R.drawable.default_avatar)
        }

        holder.btnAccept.setOnClickListener { onAccept(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun addOrUpdate(item: FollowRequest) {
        val idx = items.indexOfFirst { it.requesterUid == item.requesterUid }
        if (idx >= 0) {
            items[idx] = item
            notifyItemChanged(idx)
        } else {
            items.add(0, item)
            notifyItemInserted(0)
        }
    }

    fun removeByUid(uid: String) {
        val idx = items.indexOfFirst { it.requesterUid == uid }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun clearAll() {
        items.clear()
        notifyDataSetChanged()
    }
}

class you_page : AppCompatActivity() {

    private val rtdb: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val meUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private lateinit var adapter: FollowRequestAdapter
    private lateinit var recycler: RecyclerView

    // Listener refs
    private var reqRef: DatabaseReference? = null
    private var reqListener: ChildEventListener? = null

    fun loadBottomBarAvatar(navProfile: ImageView) {
        val uid = FirebaseAuth.getInstance().uid ?: return
        val ref = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(uid)
            .child("profilePictureUrl")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val b64 = snapshot.getValue(String::class.java) ?: return
                val clean = b64.substringAfter(",", b64)
                val bytes = try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { null } ?: return
                Glide.with(navProfile.context)
                    .asBitmap()
                    .load(bytes)
                    .placeholder(R.drawable.oval)
                    .error(R.drawable.oval)
                    .circleCrop()
                    .into(navProfile)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_you_page)

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
        findViewById<ImageView>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, home_page::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_search).setOnClickListener {
            startActivity(Intent(this, search_feed::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_create).setOnClickListener {
            startActivity(Intent(this, posting::class.java)); finish()
        }
        findViewById<ImageView>(R.id.nav_profile).setOnClickListener {
            startActivity(Intent(this, my_profile::class.java)); finish()
        }

        // Recycler setup
        recycler = findViewById(R.id.requestsRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = FollowRequestAdapter(mutableListOf(),
            onAccept = { item -> acceptRequest(item.requesterUid) },
            onDelete = { item -> declineRequest(item.requesterUid) }
        )
        recycler.adapter = adapter

        attachRequestsListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        reqListener?.let { l -> reqRef?.removeEventListener(l) }
    }

    private fun attachRequestsListener() {
        val my = meUid ?: return
        reqRef = rtdb.child("follow_requests").child(my)

        // keep list live and in sync
        reqListener = reqRef!!.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val requesterUid = snapshot.key ?: return
                fetchRequester(requesterUid) { req ->
                    adapter.addOrUpdate(req)
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val requesterUid = snapshot.key ?: return
                if (snapshot.getValue(Boolean::class.java) != true) {
                    adapter.removeByUid(requesterUid)
                } else {
                    fetchRequester(requesterUid) { req -> adapter.addOrUpdate(req) }
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                val requesterUid = snapshot.key ?: return
                adapter.removeByUid(requesterUid)
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchRequester(uid: String, done: (FollowRequest) -> Unit) {
        rtdb.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val username = s.child("username").getValue(String::class.java) ?: ""
                    val photo = s.child("profilePictureUrl").getValue(String::class.java)
                    done(FollowRequest(uid, username, photo))
                }
                override fun onCancelled(error: DatabaseError) {
                    done(FollowRequest(uid, uid.take(8), null))
                }
            })
    }

    // --- Actions ---

    private fun acceptRequest(requesterUid: String) {
        val my = meUid ?: return
        val updates = hashMapOf<String, Any?>(
            "/followers/$my/$requesterUid" to true,
            "/following/$requesterUid/$my" to true,
            "/follow_requests/$my/$requesterUid" to null
        )
        rtdb.updateChildren(updates).addOnFailureListener {
            // if it fails, leave the row; listener will handle success case removal
        }
    }

    private fun declineRequest(requesterUid: String) {
        val my = meUid ?: return
        rtdb.child("follow_requests").child(my).child(requesterUid)
            .removeValue()
            .addOnFailureListener {
                // same note as above
            }
    }
}
