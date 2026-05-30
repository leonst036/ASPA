package com.aspa.plugin.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val LEGACY_ITERATIONS = 1000
    private const val CURRENT_ITERATIONS = 210_000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "PBKDF2"

    fun hashPassword(password: String): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val salt = Base64.getEncoder().encodeToString(saltBytes)

        val hash = deriveHash(password, saltBytes, CURRENT_ITERATIONS)
        val hashStr = Base64.getEncoder().encodeToString(hash)

        return "$PREFIX:$CURRENT_ITERATIONS:$salt:$hashStr"
    }

    fun verifyPassword(password: String, storedHashWithSalt: String): Boolean {
        val parsed = parseHash(storedHashWithSalt) ?: return false
        val candidate = deriveHash(password, parsed.salt, parsed.iterations)
        return MessageDigest.isEqual(candidate, parsed.hash)
    }

    fun needsRehash(storedHashWithSalt: String): Boolean {
        val parsed = parseHash(storedHashWithSalt) ?: return false
        return parsed.iterations < CURRENT_ITERATIONS || parsed.isLegacy
    }

    private data class ParsedHash(
        val iterations: Int,
        val salt: ByteArray,
        val hash: ByteArray,
        val isLegacy: Boolean
    )

    private fun parseHash(storedHashWithSalt: String): ParsedHash? {
        val parts = storedHashWithSalt.split(":")
        return when {
            parts.size == 2 -> {
                val saltBytes = decodeBase64(parts[0]) ?: return null
                val hashBytes = decodeBase64(parts[1]) ?: return null
                ParsedHash(LEGACY_ITERATIONS, saltBytes, hashBytes, true)
            }
            parts.size == 4 && parts[0] == PREFIX -> {
                val iterations = parts[1].toIntOrNull() ?: return null
                val saltBytes = decodeBase64(parts[2]) ?: return null
                val hashBytes = decodeBase64(parts[3]) ?: return null
                ParsedHash(iterations, saltBytes, hashBytes, false)
            }
            else -> null
        }
    }

    private fun decodeBase64(value: String): ByteArray? {
        return try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun deriveHash(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }
}
