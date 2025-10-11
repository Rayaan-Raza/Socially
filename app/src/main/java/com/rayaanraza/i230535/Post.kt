package com.rayaanraza.i230535

data class Post(   val postId: String = "",
                   val uid: String = "",
                   val username: String = "",
                   val imageUrl: String = "",
                   val imageBase64: String = "",
                   val caption: String = "",
                   val createdAt: Long = 0L,
                   val likeCount: Long = 0L,
                   val commentCount: Long = 0L)
