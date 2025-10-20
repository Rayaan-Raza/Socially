package com.group.i230535_i230048

import com.google.firebase.database.Exclude

data class Post(val postId: String = "",
                val uid: String = "",
                var username: String = "",
                val caption: String = "",
                val imageUrl: String = "",
                val imageBase64: String = "",
                val createdAt: Long = 0,
                val likeCount: Long = 0,
                val commentCount: Long = 0,
                @get:Exclude var commentPreview: List<Comment> = emptyList()
)
