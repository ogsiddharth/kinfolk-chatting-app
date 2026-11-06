package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.R
import com.example.data.model.AuditLog
import com.example.data.model.Invitation
import com.example.data.model.UserProfile
import com.example.util.SecurityHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

private const val TAG = "AuthRepository"
private const val PREFS_NAME = "kinfolk_auth_prefs"
private const val KEY_LOGGED_IN_UID = "logged_in_uid"

class AuthRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
    }

    private val _currentUserState = MutableStateFlow<UserProfile?>(null)
    val currentUserState: StateFlow<UserProfile?> = _currentUserState.asStateFlow()

    init {
        val savedUid = prefs.getString(KEY_LOGGED_IN_UID, null)
        if (!savedUid.isNullOrEmpty()) {
            loadUserProfile(savedUid)
        }
    }

    fun getSavedUid(): String? = prefs.getString(KEY_LOGGED_IN_UID, null)

    private fun loadUserProfile(uid: String) {
        db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing user profile: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                _currentUserState.value = profile
            } else {
                _currentUserState.value = null
            }
        }
    }

    suspend fun hasAnyRegisteredOwner(): Boolean = withContext(Dispatchers.IO) {
        val authUser = com.google.firebase.Firebase.auth.currentUser
        if (authUser == null) {
            return@withContext false
        }
        try {
            val snapshot = db.collection("users")
                .whereEqualTo("role", "owner")
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking owner registration: ${e.message}")
            false
        }
    }

    suspend fun fetchProfileForCurrentAuthUser(): UserProfile? = withContext(Dispatchers.IO) {
        val uid = com.google.firebase.Firebase.auth.currentUser?.uid ?: return@withContext null
        try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val profile = doc.toObject(UserProfile::class.java)
                _currentUserState.value = profile
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile for auth user: ${e.message}")
            null
        }
    }

    /**
     * Bootstrap the first family owner account and generate an initial invite code for Sister.
     */
    suspend fun bootstrapOwner(
        username: String,
        displayName: String,
        password: String
    ): Result<Pair<UserProfile, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.lowercase().trim()
            val cleanDisplay = displayName.trim()
            val uid = com.google.firebase.Firebase.auth.currentUser?.uid ?: SecurityHelper.generateUniqueId()
            val passwordHash = SecurityHelper.hashPassword(password, cleanUsername)
            val recoveryCode = SecurityHelper.generateRecoveryCode()
            val recoveryHash = SecurityHelper.hashPassword(recoveryCode, cleanUsername)

            val ownerProfile = UserProfile(
                userId = uid,
                username = cleanUsername,
                displayName = cleanDisplay,
                role = "owner",
                passwordHash = passwordHash,
                recoveryCodeHash = recoveryHash,
                isOnline = true,
                showLastSeen = true,
                showReadReceipts = true,
                showTyping = true,
                blockScreenshots = false,
                isLocked = false
            )

            // Save user profile
            db.collection("users").document(uid).set(ownerProfile).await()

            // Save admin designation
            val adminData = mapOf(
                "userId" to uid,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("admins").document(uid).set(adminData).await()

            // Generate first instant invitation code for sister
            val sisterInviteCode = SecurityHelper.generateInviteCode()
            val expiry = Timestamp(Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)) // 7 days
            val invite = Invitation(
                code = sisterInviteCode,
                createdBy = uid,
                status = "pending",
                expiresAt = expiry
            )
            db.collection("invitations").document(sisterInviteCode).set(invite).await()

            // Record audit log
            val log = AuditLog(
                id = SecurityHelper.generateUniqueId(),
                action = "FAMILY_SPACE_INITIALIZED",
                performedBy = uid,
                details = "Family space initialized by owner $cleanDisplay ($cleanUsername). Sister invite $sisterInviteCode generated.",
                timestamp = Timestamp.now()
            )
            db.collection("audit_logs").document(log.id).set(log).await()

            // Save session
            prefs.edit().putString(KEY_LOGGED_IN_UID, uid).commit()
            loadUserProfile(uid)

            Result.success(Pair(ownerProfile, sisterInviteCode))
        } catch (e: Exception) {
            Log.e(TAG, "Error bootstrapping owner", e)
            Result.failure(e)
        }
    }

    /**
     * Join with an invitation code (e.g. Sister onboarding).
     */
    suspend fun joinWithInvitation(
        inviteCode: String,
        username: String,
        displayName: String,
        password: String
    ): Result<Pair<UserProfile, String>> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = inviteCode.uppercase().trim()
            val cleanUsername = username.lowercase().trim()
            val cleanDisplay = displayName.trim()

            // 1. Verify invite code
            val inviteDoc = db.collection("invitations").document(cleanCode).get().await()
            if (!inviteDoc.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid invitation code."))
            }
            val invitation = inviteDoc.toObject(Invitation::class.java)
                ?: return@withContext Result.failure(IllegalArgumentException("Corrupted invitation."))

            if (invitation.status != "pending") {
                return@withContext Result.failure(IllegalStateException("Invitation has already been used or revoked."))
            }
            if (invitation.expiresAt != null && invitation.expiresAt.toDate().before(Date())) {
                return@withContext Result.failure(IllegalStateException("Invitation code has expired."))
            }

            // 2. Check username availability
            val existingUser = db.collection("users")
                .whereEqualTo("username", cleanUsername)
                .limit(1)
                .get()
                .await()
            if (!existingUser.isEmpty) {
                return@withContext Result.failure(IllegalArgumentException("Username '$cleanUsername' is already taken."))
            }

            // 3. Create member account
            val uid = com.google.firebase.Firebase.auth.currentUser?.uid ?: SecurityHelper.generateUniqueId()
            val passwordHash = SecurityHelper.hashPassword(password, cleanUsername)
            val recoveryCode = SecurityHelper.generateRecoveryCode()
            val recoveryHash = SecurityHelper.hashPassword(recoveryCode, cleanUsername)

            val memberProfile = UserProfile(
                userId = uid,
                username = cleanUsername,
                displayName = cleanDisplay,
                role = "member",
                passwordHash = passwordHash,
                recoveryCodeHash = recoveryHash,
                isOnline = true,
                showLastSeen = true,
                showReadReceipts = true,
                showTyping = true,
                blockScreenshots = false,
                isLocked = false
            )

            db.collection("users").document(uid).set(memberProfile).await()

            // 4. Mark invitation as used
            db.collection("invitations").document(cleanCode).update(
                mapOf(
                    "status" to "used",
                    "usedBy" to uid,
                    "usedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 5. Audit log
            val log = AuditLog(
                id = SecurityHelper.generateUniqueId(),
                action = "MEMBER_JOINED",
                performedBy = uid,
                details = "New family member $cleanDisplay ($cleanUsername) joined using invite code $cleanCode.",
                timestamp = Timestamp.now()
            )
            db.collection("audit_logs").document(log.id).set(log).await()

            // 6. Save session
            prefs.edit().putString(KEY_LOGGED_IN_UID, uid).commit()
            loadUserProfile(uid)

            Result.success(Pair(memberProfile, recoveryCode))
        } catch (e: Exception) {
            Log.e(TAG, "Error joining with invitation", e)
            Result.failure(e)
        }
    }

    /**
     * Log in using Username & Password.
     */
    suspend fun login(username: String, password: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.lowercase().trim()
            val passwordHash = SecurityHelper.hashPassword(password, cleanUsername)

            val snapshot = db.collection("users")
                .whereEqualTo("username", cleanUsername)
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return@withContext Result.failure(IllegalArgumentException("No account found with username '$cleanUsername'."))
            }

            val doc = snapshot.documents.first()
            val profile = doc.toObject(UserProfile::class.java)
                ?: return@withContext Result.failure(IllegalStateException("Invalid user profile."))

            if (profile.isLocked) {
                return@withContext Result.failure(IllegalStateException("This account has been locked by the family owner."))
            }

            if (profile.passwordHash != passwordHash) {
                return@withContext Result.failure(IllegalArgumentException("Incorrect password."))
            }

            // Update presence
            db.collection("users").document(profile.userId).update(
                mapOf(
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp()
                )
            ).await()

            // Save session locally
            prefs.edit().putString(KEY_LOGGED_IN_UID, profile.userId).commit()
            loadUserProfile(profile.userId)

            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    /**
     * Account recovery using recovery code.
     */
    suspend fun recoverAccount(
        username: String,
        recoveryCode: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.lowercase().trim()
            val cleanRecovery = recoveryCode.uppercase().trim()
            val recoveryHash = SecurityHelper.hashPassword(cleanRecovery, cleanUsername)

            val snapshot = db.collection("users")
                .whereEqualTo("username", cleanUsername)
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return@withContext Result.failure(IllegalArgumentException("No account found with username '$cleanUsername'."))
            }

            val doc = snapshot.documents.first()
            val profile = doc.toObject(UserProfile::class.java)
                ?: return@withContext Result.failure(IllegalStateException("Invalid user data."))

            if (profile.recoveryCodeHash != recoveryHash) {
                return@withContext Result.failure(IllegalArgumentException("Invalid recovery code."))
            }

            val newPasswordHash = SecurityHelper.hashPassword(newPassword, cleanUsername)
            db.collection("users").document(profile.userId).update(
                mapOf(
                    "passwordHash" to newPasswordHash,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Account recovery failed", e)
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        userId: String,
        showLastSeen: Boolean,
        showReadReceipts: Boolean,
        showTyping: Boolean,
        blockScreenshots: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("users").document(userId).update(
                mapOf(
                    "showLastSeen" to showLastSeen,
                    "showReadReceipts" to showReadReceipts,
                    "showTyping" to showTyping,
                    "blockScreenshots" to blockScreenshots,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("users").document(userId).update(
                mapOf(
                    "displayName" to displayName.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(userId: String?) = withContext(Dispatchers.IO) {
        if (!userId.isNullOrEmpty()) {
            try {
                db.collection("users").document(userId).update(
                    mapOf(
                        "isOnline" to false,
                        "lastSeen" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Error setting offline on logout: ${e.message}")
            }
        }
        prefs.edit().remove(KEY_LOGGED_IN_UID).commit()
        _currentUserState.value = null
    }
}
