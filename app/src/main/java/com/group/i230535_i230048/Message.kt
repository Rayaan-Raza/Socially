package com.group.i230535_i230048

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageType: String = "text", // "text", "image", "post"
    val content: String = "",
    val imageUrl: String = "",
    val postId: String = "",
    val sharedPostId: String = "",
    val text : String = "",
    val timestamp: Long = 0L,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val editableUntil: Long = 0L // timestamp + 5 minutes (300000 ms)
) {
    // Check if message can still be edited/deleted (within 5 minutes)
    fun canEditOrDelete(): Boolean {
        return System.currentTimeMillis() < editableUntil && !isDeleted
    }

    // Get display content
    fun getDisplayContent(): String {
        return when {
            isDeleted -> "This message was deleted"
            messageType == "image" -> "📷 Photo"
            messageType == "post" -> "📝 Shared a post"
            else -> content
        }
    }
}

data class Chat(
    val chatId: String = "",
    val participant1: String = "",
    val participant2: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = ""
) {
    // Get the other user's ID (not yours)
    fun getOtherUserId(currentUserId: String): String {
        return if (participant1 == currentUserId) participant2 else participant1
    }
}

data class User(
    val uid: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val profilePictureUrl: String = "",
    val photo: String = "",
    val bio: String = "",
    val website: String = "",
    val phoneNumber: String = "",
    val gender: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0
)