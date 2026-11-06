package com.example

import com.example.util.SecurityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityHelperTest {

    @Test
    fun testPasswordHashing() {
        val hash1 = SecurityHelper.hashPassword("secret123", "sister")
        val hash2 = SecurityHelper.hashPassword("secret123", "sister")
        val hash3 = SecurityHelper.hashPassword("secret123", "brother")

        assertEquals(hash1, hash2)
        assertNotEquals(hash1, hash3)
        assertEquals(64, hash1.length) // SHA-256 hex string length
    }

    @Test
    fun testGenerateInviteCode() {
        val code = SecurityHelper.generateInviteCode()
        assertTrue(code.startsWith("KIN-"))
        assertEquals(8, code.length)
    }

    @Test
    fun testGenerateRecoveryCode() {
        val code = SecurityHelper.generateRecoveryCode()
        assertTrue(code.startsWith("KIN-"))
        val parts = code.split("-")
        assertEquals(3, parts.size)
        assertEquals("KIN", parts[0])
        assertEquals(4, parts[1].length)
        assertEquals(4, parts[2].length)
    }
}
