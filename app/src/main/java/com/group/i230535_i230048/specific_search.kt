package com.group.i230535_i230048

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.net.URLEncoder

class specific_search : AppCompatActivity() {

    private val TAG = "SPECIFIC_SEARCH_HH"

    private lateinit var searchEditText: EditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val userList = mutableListOf<User>()

    private lateinit var cancelButton: TextView

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val DEBOUNCE_DELAY = 400L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_specific_search)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        searchEditText = findViewById(R.id.search)
        cancelButton = findViewById(R.id.cancel_button)

        setupRecyclerView()
        setupSearchListener()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchAdapter = SearchAdapter(this, userList)
        searchResultsRecyclerView.adapter = searchAdapter
    }

    private fun setupSearchListener() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    // Cancel any pending search
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }

                    val searchText = s?.toString()?.trim() ?: ""

                    if (searchText.isNotEmpty()) {
                        cancelButton.visibility = View.VISIBLE

                        // Debounce the search
                        searchRunnable = Runnable {
                            searchForUsers(searchText)
                        }
                        searchHandler.postDelayed(searchRunnable!!, DEBOUNCE_DELAY)
                    } else {
                        cancelButton.visibility = View.GONE
                        // Clear results safely
                        userList.clear()
                        searchAdapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onTextChanged: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchForUsers(searchText: String) {
        try {
            Log.d(TAG, "Searching for: $searchText")

            val queue = Volley.newRequestQueue(this)
            val encodedQuery = URLEncoder.encode(searchText, "UTF-8")
            val url = AppGlobals.BASE_URL + "users_search.php?q=$encodedQuery"

            val stringRequest = StringRequest(Request.Method.GET, url,
                { response ->
                    try {
                        Log.d(TAG, "Response received")
                        val json = JSONObject(response)

                        if (json.optBoolean("success", false)) {
                            val dataArray = json.optJSONArray("data")

                            val newUsers = mutableListOf<User>()

                            if (dataArray != null) {
                                for (i in 0 until dataArray.length()) {
                                    try {
                                        val userObj = dataArray.getJSONObject(i)

                                        val user = User(
                                            uid = userObj.optString("uid", ""),
                                            username = userObj.optString("username", "user"),
                                            firstName = userObj.optString("firstName", ""),
                                            lastName = userObj.optString("lastName", ""),
                                            fullName = userObj.optString("fullName", ""),
                                            email = userObj.optString("email", ""),
                                            profilePictureUrl = userObj.optString("profilePictureUrl", ""),
                                            photo = userObj.optString("photo", ""),
                                            bio = userObj.optString("bio", ""),
                                            website = userObj.optString("website", ""),
                                            phoneNumber = userObj.optString("phoneNumber", ""),
                                            gender = userObj.optString("gender", ""),
                                            isOnline = userObj.optBoolean("isOnline", false),
                                            lastSeen = userObj.optLong("lastSeen", 0L),
                                            followersCount = userObj.optInt("followersCount", 0),
                                            followingCount = userObj.optInt("followingCount", 0),
                                            postsCount = userObj.optInt("postsCount", 0)
                                        )

                                        newUsers.add(user)
                                        Log.d(TAG, "Parsed user: ${user.username}")

                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing user $i: ${e.message}")
                                    }
                                }
                            }

                            // Update UI on main thread
                            runOnUiThread {
                                userList.clear()
                                userList.addAll(newUsers)
                                searchAdapter.notifyDataSetChanged()
                                Log.d(TAG, "Updated adapter with ${userList.size} users")
                            }

                        } else {
                            Log.w(TAG, "API error: ${json.optString("message")}")
                            runOnUiThread {
                                userList.clear()
                                searchAdapter.notifyDataSetChanged()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parse error: ${e.message}")
                        e.printStackTrace()
                    }
                },
                { error ->
                    Log.e(TAG, "Volley error: ${error.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Search failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            queue.add(stringRequest)

        } catch (e: Exception) {
            Log.e(TAG, "Error in searchForUsers: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        cancelButton.setOnClickListener {
            // Clear search and go back
            searchEditText.text.clear()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
    }
}