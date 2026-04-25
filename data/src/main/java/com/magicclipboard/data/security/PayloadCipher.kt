package com.magicclipboard.data.security

interface PayloadCipher {
    fun encryptText(plainText: String): String

    fun decryptText(payload: String): String

    fun encryptBytes(bytes: ByteArray): ByteArray

    fun decryptBytes(bytes: ByteArray): ByteArray
}

