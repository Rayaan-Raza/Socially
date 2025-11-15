package com.group.i230535_i230048 // Make sure this package is correct

public final class DB private constructor() {
    companion object {
        const val DATABASE_NAME = "socially_offline.db"
        const val DATABASE_VERSION = 1
    }

    // Corresponds to your User.kt model
    public final class User private constructor() {
        companion object {
            const val TABLE_NAME = "users"
            const val COLUMN_UID = "uid"
            const val COLUMN_USERNAME = "username"
            const val COLUMN_FULL_NAME = "fullName"
            const val COLUMN_PROFILE_PIC_URL = "profilePictureUrl"
            const val COLUMN_EMAIL = "email"
            const val COLUMN_BIO = "bio"
            const val COLUMN_IS_ONLINE = "isOnline"
            const val COLUMN_LAST_SEEN = "lastSeen"
        }
    }

    // Corresponds to your Post.kt model
    public final class Post private constructor() {
        companion object {
            const val TABLE_NAME = "posts"
            const val COLUMN_POST_ID = "postId"
            const val COLUMN_UID = "uid"
            const val COLUMN_USERNAME = "username"
            const val COLUMN_CAPTION = "caption"
            const val COLUMN_IMAGE_URL = "imageUrl"
            // We will save Base64 as text, though this is not ideal for large images
            const val COLUMN_IMAGE_BASE64 = "imageBase64"
            const val COLUMN_CREATED_AT = "createdAt"
            const val COLUMN_LIKE_COUNT = "likeCount"
            const val COLUMN_COMMENT_COUNT = "commentCount"
        }
    }

    // Corresponds to your Comment.kt model
    public final class Comment private constructor() {
        companion object {
            const val TABLE_NAME = "comments"
            const val COLUMN_COMMENT_ID = "commentId"
            const val COLUMN_POST_ID = "postId"
            const val COLUMN_UID = "uid"
            const val COLUMN_USERNAME = "username"
            const val COLUMN_TEXT = "text"
            const val COLUMN_CREATED_AT = "createdAt"
        }
    }

    // Corresponds to your Message.kt model
    public final class Message private constructor() {
        companion object {
            const val TABLE_NAME = "messages"
            const val COLUMN_MESSAGE_ID = "messageId"
            const val COLUMN_SENDER_ID = "senderId"
            const val COLUMN_RECEIVER_ID = "receiverId"
            const val COLUMN_MESSAGE_TYPE = "messageType"
            const val COLUMN_CONTENT = "content" // For text content
            const val COLUMN_IMAGE_URL = "imageUrl"
            const val COLUMN_POST_ID = "postId" // For shared posts
            const val COLUMN_TIMESTAMP = "timestamp"
            const val COLUMN_IS_EDITED = "isEdited"
            const val COLUMN_IS_DELETED = "isDeleted"
        }
    }

    // Corresponds to your Story_data.kt model
    public final class Story private constructor() {
        companion object {
            const val TABLE_NAME = "stories"
            const val COLUMN_STORY_ID = "storyId"
            const val COLUMN_MEDIA_URL = "mediaUrl"
            const val COLUMN_MEDIA_TYPE = "mediaType"
            const val COLUMN_CREATED_AT = "createdAt"
            const val COLUMN_EXPIRES_AT = "expiresAt"
            // We also need to know who posted it
            const val COLUMN_UID = "uid"
        }
    }
    public final class ChatSessionInfo private constructor() {
        companion object {
            const val TABLE_NAME = "chat_sessions"
            const val COLUMN_CHAT_ID = "chatId"
            const val COLUMN_OTHER_USER_ID = "otherUserId"
            const val COLUMN_OTHER_USERNAME = "otherUsername"
            const val COLUMN_OTHER_PIC_URL = "otherProfilePicUrl"
            const val COLUMN_LAST_MESSAGE = "lastMessage"
            const val COLUMN_LAST_MESSAGE_TIMESTAMP = "lastMessageTimestamp"
            const val COLUMN_LAST_MESSAGE_SENDER_ID = "lastMessageSenderId"
        }
    }
    // For Task #13: Offline Sync Queue
    public final class SyncQueue private constructor() {
        companion object {
            const val TABLE_NAME = "sync_queue"
            const val COLUMN_ID = "id" // Local auto-incrementing ID
            const val COLUMN_ENDPOINT = "endpoint" // e.g., "like_post.php"
            const val COLUMN_PAYLOAD = "payload" // JSON string of the request
            const val COLUMN_STATUS = "status" // e.g., "PENDING"
        }
    }
}