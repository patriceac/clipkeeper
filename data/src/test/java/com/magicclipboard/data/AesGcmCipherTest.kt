package com.magicclipboard.data

import com.magicclipboard.data.security.AesGcmCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCipherTest {
    private val cipher = AesGcmCipher(
        secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
    )

    @Test
    fun `round trips text payloads`() {
        val encrypted = cipher.encryptText("bonjour clipboard")

        assertEquals("bonjour clipboard", cipher.decryptText(encrypted))
    }

    @Test
    fun `round trips binary payloads`() {
        val input = byteArrayOf(1, 2, 3, 4, 5)

        assertArrayEquals(input, cipher.decryptBytes(cipher.encryptBytes(input)))
    }
}

