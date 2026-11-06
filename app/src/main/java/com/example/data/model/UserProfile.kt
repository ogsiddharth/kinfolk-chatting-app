package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "member", // "owner" or "member"
    val passwordHash: String = "",
    val recoveryCodeHash: String = "",
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    @ServerTimestamp val lastSeen: Timestamp? = null,
    val showLastSeen: Boolean = true,
    val showReadReceipts: Boolean = true,
    val showTyping: Boolean = true,
    val blockScreenshots: Boolean = false,
    val isLocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
