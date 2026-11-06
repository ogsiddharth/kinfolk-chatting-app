package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Invitation(
    @DocumentId val code: String = "",
    val createdBy: String = "",
    val status: String = "pending", // "pending", "used", "revoked"
    val usedBy: String? = null,
    val usedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
