package com.rayaanraza.i230535

data class ChatSession(
    val chatId: String = "",
    val participants: Map<String, Boolean> = emptyMap(), // userId -> true
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: String = ""
) {
    // Helper to get the other user's ID (not yours)
    fun getOtherUserId(currentUserId: String): String {
        return participants.keys.firstOrNull { it != currentUserId } ?: ""
    }
}