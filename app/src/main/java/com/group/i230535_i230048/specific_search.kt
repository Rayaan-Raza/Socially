package com.group.i230535_i230048

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log // CHANGED
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// REMOVED: Firebase imports
import com.android.volley.Request // CHANGED
import com.android.volley.RequestQueue // CHANGED
import com.android.volley.toolbox.StringRequest // CHANGED
import com.android.volley.toolbox.Volley // CHANGED
import com.google.gson.Gson // CHANGED
import com.google.gson.reflect.TypeToken // CHANGED
import org.json.JSONObject // CHANGED

class specific_search : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val userList = mutableListOf<User>()

    // CHANGED: Added Volley queue
    private lateinit var queue: RequestQueue

    private lateinit var cancelButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_specific_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // CHANGED: Initialize Volley
        queue = Volley.newRequestQueue(this)

        // Initialize Views
        searchEditText = findViewById(R.id.search)
        cancelButton = findViewById(R.id.cancel_button)

        setupRecyclerView()
        setupSearchListener()
        setupExistingClickListeners()
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
                val searchText = s.toString().lowercase().trim()
                if (searchText.isNotEmpty()) {
                    cancelButton.visibility = View.VISIBLE
                    // CHANGED: Call new API function
                    searchForUsers(searchText)
                } else {
                    cancelButton.visibility = View.GONE
                    userList.clear()
                    searchAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // --- CHANGED: Migrated to Volley API call ---
    private fun searchForUsers(searchText: String) {
        // TODO: Dev A needs to create this API endpoint
        val url = AppGlobals.BASE_URL + "search_users.php?query=$searchText"

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getBoolean("success")) {
                        val dataArray = json.getJSONArray("data")

                        val listType = object : TypeToken<List<User>>() {}.type
                        val users: List<User> = Gson().fromJson(dataArray.toString(), listType)

                        userList.clear()
                        userList.addAll(users)
                        searchAdapter.notifyDataSetChanged()

                    } else {
                        Log.w("specific_search", "API error: ${json.getString("message")}")
                        userList.clear()
                        searchAdapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    Log.e("specific_search", "Error parsing search results: ${e.message}")
                }
            },
            { error ->
                Log.e("specific_search", "Volley error searching users: ${error.message}")
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }
        )
        queue.add(stringRequest)
    }

    private fun setupExistingClickListeners() {
        // (No changes here)
        cancelButton.setOnClickListener {
            searchEditText.text.clear()
        }
        findViewById<TextView>(R.id.cancel_button).setOnClickListener {
            finish()
        }
    }
}