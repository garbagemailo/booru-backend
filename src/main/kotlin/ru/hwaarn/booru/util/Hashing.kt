package ru.hwaarn.booru.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun hash(password: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val encoded = pbkdf2(password.toCharArray(), salt)
        return salt.toHex() + ":" + encoded.toHex()
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytes()
        val expected = parts[1]
        val actual = pbkdf2(password.toCharArray(), salt).toHex()
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

object Digests {
    fun md5(bytes: ByteArray): String = digest("MD5", bytes)
    fun sha256(bytes: ByteArray): String = digest("SHA-256", bytes)
    fun sha256(text: String): String = digest("SHA-256", text.toByteArray())

    private fun digest(algorithm: String, bytes: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(bytes).toHex()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
