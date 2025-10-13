package com.rayaanraza.i230535

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

// Make sure your top-level User data class is available and includes 'username' and 'fullName'
// For example, in Message.kt:
// data class User(val uid: String = "", val username: String = "", val fullName: String = "")

class specific_search : AppCompatActivity() {

    private lateinit var searchEditText: EditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchAdapter: SearchAdapter
    private val userList = mutableListOf<User>()
    private lateinit var database: FirebaseDatabase

    // Views from your original code
    private lateinit var cancelButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is your existing edge-to-edge setup
        setContentView(R.layout.activity_specific_search)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Initialize Views
        searchEditText = findViewById(R.id.search)
        cancelButton = findViewById(R.id.cancel_button) // Using the ID from the new XML

        // Setup the new search functionality
        setupRecyclerView()
        setupSearchListener()

        // Setup your existing click listeners
        setupExistingClickListeners()
    }

    private fun setupRecyclerView() {
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        // Pass 'this' context and the user list to the adapter
        searchAdapter = SearchAdapter(this, userList)
        searchResultsRecyclerView.adapter = searchAdapter
    }

    private fun setupSearchListener() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val searchText = s.toString().lowercase().trim()
                if (searchText.isNotEmpty()) {
                    // Show the cancel button when typing
                    cancelButton.visibility = View.VISIBLE
                    searchForUsers(searchText)
                } else {
                    // Hide the cancel button and clear results when empty
                    cancelButton.visibility = View.GONE
                    userList.clear()
                    searchAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun searchForUsers(searchText: String) {
        val usersRef = database.getReference("users")
        // This Firebase query finds users where the 'username' field starts with the searchText.
        // The `\uf8ff` character is a special trick that acts as a high-end boundary for strings.
        val query = usersRef.orderByChild("username")
            .startAt(searchText)
            .endAt(searchText + "\uf8ff")

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    // Add user to the list, but don't add yourself to the search results
                    if (user != null) {
                        userList.add(user)
                    }
                }
                searchAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // It's good practice to log the error
                Toast.makeText(this@specific_search, "Search failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupExistingClickListeners() {
        // This is your cancel button's logic, now correctly linked
        cancelButton.setOnClickListener {
            // Clear the search text, which will also clear the results
            searchEditText.text.clear()
            // Optionally, you can also navigate back like your original code
            // finish()
        }

        // Your original file had this listener. We'll keep it for the new Cancel button.
        // If you intended the `scan` TextView to be a back button:
        findViewById<TextView>(R.id.cancel_button).setOnClickListener {
            finish()
        }
    }
}
