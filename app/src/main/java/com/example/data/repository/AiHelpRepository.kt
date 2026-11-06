package com.example.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "AiHelpRepository"

class AiHelpRepository {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val systemInstructions = """
        You are the Kinfolk Family Assistant, a helpful and respectful in-app guide for the Kinfolk private family messenger.
        Kinfolk is a private, lightweight family chat app with NO phone numbers and NO stranger discovery.
        Key features you can explain:
        1. Invitations: Only the family owner can generate invitation codes (e.g., KIN-XXXX). Single-use or timed.
        2. Screenshots: Android 14+ native detection sends an alert if a screenshot is taken. Users can also enable 'Block Screenshots' in Privacy Settings (FLAG_SECURE) to prevent capture entirely.
        3. Media & Voice: Users can send voice memos (using the microphone button), photos, and documents securely without public links.
        4. Privacy: Users can toggle last seen, online presence, typing indicators, and read receipts in Settings.
        5. Account Recovery: Every user receives a private emergency recovery code (KIN-XXXX-YYYY) when joining.
        6. AI Privacy Guarantee: You ONLY answer help questions about the app and settings. You do NOT read family chat messages or store personal family discussions.
        Respond warmly, concisely, and clearly in plain text.
    """.trimIndent()

    suspend fun askAssistant(userQuestion: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val prompt = "$systemInstructions\n\nUser Question: $userQuestion\nAssistant Answer:"
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                Result.success(text.trim())
            } else {
                Result.failure(IllegalStateException("Assistant returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Assistant timed out", e)
            Result.failure(Exception("Assistant request timed out. Please check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Assistant error", e)
            Result.failure(e)
        }
    }
}
