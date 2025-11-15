package com.group.i230535_i230048.models

/**
 * User model matching API response
 */
data class User(
    val id: Int = 0,
    val uid: String = "",
    val firebase_uid: String = "",
    val email: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val dob: String = "",
    val bio: String = "Hey there! I'm using Socially",
    val website: String = "",
    val phoneNumber: String = "",
    val gender: String = "",
    val profilePictureUrl: String = "",
    val photo: String = "",
    val fcmToken: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val accountPrivate: Int = 0,
    val profileCompleted: Int = 1,
    val isOnline: Int = 1,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L
)

/**
 * Post model
 */
data class Post(
    val postId: String = "",
    val uid: String = "",
    val username: String = "",
    val userProfilePicture: String = "",
    val caption: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val createdAt: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val isLikedByCurrentUser: Boolean = false
)

/**
 * Message model
 */
data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageType: String = "text", // text, image, video, audio, file, post
    val content: String = "",
    val imageUrl: String? = null,
    val postId: String? = null,
    val timestamp: Long = 0L,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val editableUntil: Long = 0L,
    val seenAt: Long? = null,
    val vanishMode: Boolean = false
)

/**
 * Comment model
 */
data class Comment(
    val commentId: String = "",
    val postId: String = "",
    val uid: String = "",
    val username: String = "",
    val userProfilePicture: String = "",
    val text: String = "",
    val createdAt: Long = 0L
)

/**
 * Story model
 */
data class Story(
    val storyId: String = "",
    val userId: String = "",
    val username: String = "",
    val userProfilePicture: String = "",
    val mediaType: String = "image", // image or video
    val mediaUrl: String = "",
    val imageBase64: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val viewCount: Int = 0
)

/**
 * Chat Session model
 */
data class ChatSession(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val participantDetails: List<User> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0,
    val vanishMode: Boolean = false
)

/**
 * Follow Request model
 */
data class FollowRequest(
    val requestId: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromUsername: String = "",
    val fromUserProfilePicture: String = "",
    val status: String = "pending", // pending, accepted, rejected
    val createdAt: Long = 0L
)

/**
 * Notification model
 */
data class Notification(
    val notificationId: String = "",
    val userId: String = "",
    val type: String = "", // message, follow_request, like, comment, screenshot
    val title: String = "",
    val body: String = "",
    val data: Map<String, String> = emptyMap(),
    val timestamp: Long = 0L,
    val isRead: Boolean = false
)

/**
 * Agora Token Response
 */
data class AgoraTokenResponse(
    val token: String = "",
    val channelName: String = "",
    val uid: Int = 0
)