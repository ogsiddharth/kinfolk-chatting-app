package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AuditLog
import com.example.data.model.Invitation
import com.example.data.model.UserProfile
import com.example.util.SecurityHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

private const val TAG = "AdminRepository"

class AdminRepository(private val context: Context) {

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
    }

    /**
     * Generate a new invitation code with expiry (in hours or days).
     */
    suspend fun createInvitation(
        ownerId: String,
        durationDays: Int = 7
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = SecurityHelper.generateInviteCode()
            val expiry = Timestamp(Date(System.currentTimeMillis() + durationDays * 24L * 60 * 60 * 1000))
            val invite = Invitation(
                code = code,
                createdBy = ownerId,
                status = "pending",
                expiresAt = expiry
            )

            db.collection("invitations").document(code).set(invite).await()

            // Audit log
            logAudit(
                action = "INVITATION_CREATED",
                performedBy = ownerId,
                details = "Generated invitation $code valid for $durationDays days."
            )

            Result.success(code)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating invitation", e)
            Result.failure(e)
        }
    }

    /**
     * Revoke an active invitation.
     */
    suspend fun revokeInvitation(code: String, ownerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("invitations").document(code).update("status", "revoked").await()
            logAudit(
                action = "INVITATION_REVOKED",
                performedBy = ownerId,
                details = "Revoked invitation code $code."
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking invitation", e)
            Result.failure(e)
        }
    }

    /**
     * Observe all invitation codes.
     */
    fun observeInvitations(): Flow<List<Invitation>> {
        return db.collection("invitations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(Invitation::class.java) }
    }

    /**
     * Observe all family members.
     */
    fun observeAllMembers(): Flow<List<UserProfile>> {
        return db.collection("users")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { it.toObjects(UserProfile::class.java) }
    }

    /**
     * Lock or unlock a member's account.
     */
    suspend fun toggleLockMember(
        targetUserId: String,
        lock: Boolean,
        adminId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("users").document(targetUserId).update("isLocked", lock).await()
            logAudit(
                action = if (lock) "ACCOUNT_LOCKED" else "ACCOUNT_UNLOCKED",
                performedBy = adminId,
                targetUser = targetUserId,
                details = if (lock) "Locked account $targetUserId" else "Unlocked account $targetUserId"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling account lock", e)
            Result.failure(e)
        }
    }

    /**
     * Observe audit log records.
     */
    fun observeAuditLogs(): Flow<List<AuditLog>> {
        return db.collection("audit_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { it.toObjects(AuditLog::class.java) }
    }

    private suspend fun logAudit(
        action: String,
        performedBy: String,
        details: String,
        targetUser: String? = null
    ) {
        try {
            val log = AuditLog(
                id = SecurityHelper.generateUniqueId(),
                action = action,
                performedBy = performedBy,
                targetUser = targetUser,
                details = details,
                timestamp = Timestamp.now()
            )
            db.collection("audit_logs").document(log.id).set(log).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error logging audit: ${e.message}")
        }
    }
}
