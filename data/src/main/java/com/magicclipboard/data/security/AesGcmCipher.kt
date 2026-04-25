package com.magicclipboard.data.security

import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmCipher(
    private val secretKey: SecretKey,
) : PayloadCipher {
    override fun encryptText(plainText: String): String {
        val cipherPayload = encryptBytes(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipherPayload)
    }

    override fun decryptText(payload: String): String {
        val decoded = Base64.getDecoder().decode(payload)
        return decryptBytes(decoded).toString(Charsets.UTF_8)
    }

    override fun encryptBytes(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(bytes)
        return ByteBuffer.allocate(4 + iv.size + encrypted.size)
            .putInt(iv.size)
            .put(iv)
            .put(encrypted)
            .array()
    }

    override fun decryptBytes(bytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(bytes)
        val ivLength = buffer.int
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_SIZE_BITS = 128
    }
}
