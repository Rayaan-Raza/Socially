package com.group.i230535_i230048

/**
 * Updated ChatSession model matching the API response.
 * It now includes the details of the participants,
 * so the adapter doesn't need to fetch them.
 */
data class ChatSession(
    val chatId: String = "",
    val participants: List<String> = emptyList(), // List of user IDs
    val participantDetails: List<User> = emptyList(), // List of User objects
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0,
    val vanishMode: Boolean = false
) {
    /**
     * Helper to get the other user's full User object
     */
    fun getOtherUser(currentUserId: String): User? {
        return participantDetails.firstOrNull { it.uid != currentUserId }
    }
}