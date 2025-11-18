package com.group.i230535_i230048

data class Story_data(
    val storyId: String = "",
    val uid: String = "",
    val mediaUrl: String = "",
    val mediaBase64: String = "",  // Added to support PHP response
    val mediaType: String = "image",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L
)