package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val type: String = "text", // "text", "image", "audio", "document", "system_screenshot"
    val mediaUrl: String? = null,
    val mediaName: String? = null,
    val mediaSize: Long = 0L,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(), // emoji -> list of userIds
    val readBy: List<String> = emptyList(), // list of userIds
    val isEdited: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
