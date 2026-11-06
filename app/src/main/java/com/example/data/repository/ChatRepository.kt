package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.UserProfile
import com.example.util.SecurityHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "ChatRepository"

class ChatRepository(private val context: Context) {

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
    }

    /**
     * Finds an existing conversation between two users or creates a new one.
     */
    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Result<Conversation> = withContext(Dispatchers.IO) {
        try {
            val convosSnapshot = db.collection("conversations")
                .whereArrayContains("participants", currentUserId)
                .get()
                .await()

            val existing = convosSnapshot.documents.mapNotNull { it.toObject(Conversation::class.java) }
                .firstOrNull { it.participants.contains(otherUserId) }

            if (existing != null) {
                return@withContext Result.success(existing)
            }

            // Create new conversation
            val convId = SecurityHelper.generateUniqueId()
            val newConvo = Conversation(
                id = convId,
                participants = listOf(currentUserId, otherUserId),
                lastMessage = mapOf(
                    "content" to "Conversation started",
                    "senderName" to "Kinfolk",
                    "timestamp" to Timestamp.now()
                )
            )

            db.collection("conversations").document(convId).set(
                mapOf(
                    "id" to convId,
                    "participants" to listOf(currentUserId, otherUserId),
                    "lastMessage" to mapOf(
                        "content" to "Conversation started",
                        "senderName" to "Kinfolk",
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(newConvo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or create conversation", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of messages in a conversation.
     */
    fun observeMessages(conversationId: String): Flow<List<Message>> {
        return db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java)
            }
    }

    /**
     * Real-time stream of conversation metadata (e.g. pinned message, participants).
     */
    fun observeConversation(conversationId: String): Flow<Conversation?> {
        return db.collection("conversations")
            .document(conversationId)
            .snapshots()
            .map { it.toObject(Conversation::class.java) }
    }

    /**
     * Real-time stream of another user's profile (for live presence, typing, last seen).
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return db.collection("users")
            .document(userId)
            .snapshots()
            .map { it.toObject(UserProfile::class.java) }
    }

    /**
     * Send a new message.
     */
    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        senderName: String,
        content: String,
        type: String = "text",
        mediaUrl: String? = null,
        mediaName: String? = null,
        mediaSize: Long = 0L,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messageId = SecurityHelper.generateUniqueId()
            val messagePayload = mutableMapOf<String, Any>(
                "id" to messageId,
                "conversationId" to conversationId,
                "senderId" to senderId,
                "senderName" to senderName,
                "content" to content,
                "type" to type,
                "reactions" to emptyMap<String, List<String>>(),
                "readBy" to listOf(senderId),
                "isEdited" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (!mediaUrl.isNullOrEmpty()) messagePayload["mediaUrl"] = mediaUrl
            if (!mediaName.isNullOrEmpty()) messagePayload["mediaName"] = mediaName
            if (mediaSize > 0) messagePayload["mediaSize"] = mediaSize
            if (!replyToId.isNullOrEmpty()) messagePayload["replyToId"] = replyToId
            if (!replyToText.isNullOrEmpty()) messagePayload["replyToText"] = replyToText
            if (!replyToSender.isNullOrEmpty()) messagePayload["replyToSender"] = replyToSender

            val batch = db.batch()
            val msgRef = db.collection("conversations").document(conversationId).collection("messages").document(messageId)
            batch.set(msgRef, messagePayload)

            // Update conversation lastMessage
            val convRef = db.collection("conversations").document(conversationId)
            val previewText = when (type) {
                "image" -> "📷 Photo"
                "audio" -> "🎙️ Voice message"
                "document" -> "📄 Document"
                "system_screenshot" -> "⚠️ Screenshot notification"
                else -> content.take(100)
            }
            batch.update(
                convRef,
                mapOf(
                    "lastMessage" to mapOf(
                        "content" to previewText,
                        "senderId" to senderId,
                        "senderName" to senderName,
                        "timestamp" to FieldValue.serverTimestamp()
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            Result.failure(e)
        }
    }

    /**
     * Broadcast native screenshot detection notification into conversation.
     */
    suspend fun notifyScreenshotDetected(
        conversationId: String,
        senderId: String,
        senderName: String
    ): Result<String> {
        return sendMessage(
            conversationId = conversationId,
            senderId = senderId,
            senderName = senderName,
            content = "$senderName captured a screenshot of the chat",
            type = "system_screenshot"
        )
    }

    /**
     * Edit message content.
     */
    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newContent: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "content" to newContent.trim(),
                        "isEdited" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error editing message", e)
            Result.failure(e)
        }
    }

    /**
     * Delete message.
     */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message", e)
            Result.failure(e)
        }
    }

    /**
     * Toggle emoji reaction on a message.
     */
    suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val rawReactions = snapshot.get("reactions") as? Map<*, *> ?: emptyMap<String, List<String>>()
                val currentReactions = mutableMapOf<String, List<String>>()

                for ((key, value) in rawReactions) {
                    if (key is String && value is List<*>) {
                        currentReactions[key] = value.filterIsInstance<String>()
                    }
                }

                val userList = currentReactions[emoji]?.toMutableList() ?: mutableListOf()
                if (userList.contains(userId)) {
                    userList.remove(userId)
                    if (userList.isEmpty()) {
                        currentReactions.remove(emoji)
                    } else {
                        currentReactions[emoji] = userList
                    }
                } else {
                    userList.add(userId)
                    currentReactions[emoji] = userList
                }

                transaction.update(docRef, "reactions", currentReactions)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling reaction", e)
            Result.failure(e)
        }
    }

    /**
     * Mark message as read.
     */
    suspend fun markAsRead(
        conversationId: String,
        messageId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .document(messageId)

            docRef.update("readBy", FieldValue.arrayUnion(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pin or unpin a message.
     */
    suspend fun togglePinMessage(
        conversationId: String,
        messageId: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.collection("conversations")
                .document(conversationId)
                .update(
                    mapOf(
                        "pinnedMessageId" to messageId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling pin", e)
            Result.failure(e)
        }
    }

    /**
     * Update user typing indicator status.
     */
    suspend fun setTyping(userId: String, isTyping: Boolean) = withContext(Dispatchers.IO) {
        try {
            db.collection("users").document(userId).update(
                mapOf("isTyping" to isTyping)
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error setting typing status: ${e.message}")
        }
    }
}
