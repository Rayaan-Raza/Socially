package com.group.i230535_i230048

data class StoryBubble(
    val uid: String = "",
    val username: String = "",
    val profileUrl: String? = null,

    // --- ADDED TO MATCH stories_bubbles_get.php API ---
    val hasStories: Boolean = false
)