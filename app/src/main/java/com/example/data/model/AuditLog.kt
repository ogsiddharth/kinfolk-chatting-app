package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val performedBy: String = "",
    val targetUser: String? = null,
    val details: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)
