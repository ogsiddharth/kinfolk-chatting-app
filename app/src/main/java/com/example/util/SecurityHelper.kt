package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object SecurityHelper {
    private val secureRandom = SecureRandom()

    /**
     * Hashes password using SHA-256 with static application salt + username salt.
     */
    fun hashPassword(password: String, username: String): String {
        val salt = "kinfolk_family_salt_${username.lowercase().trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a 12-character emergency recovery key formatted as KIN-XXXX-YYYY
     */
    fun generateRecoveryCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val part1 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        val part2 = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        return "KIN-$part1-$part2"
    }

    /**
     * Generates an invitation code formatted as KIN-XXXX
     */
    fun generateInviteCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val code = (1..4).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
        return "KIN-$code"
    }

    fun generateUniqueId(): String = UUID.randomUUID().toString()
}
