package com.cody.home.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implements the exact HMAC-SHA256 scheme from the 2026-09-03 Cody Home
 * Gateway auth handoff. Canonical string, in order, newline-separated, no
 * trailing newline:
 *   METHOD_UPPERCASE
 *   PATH               (the internal/signed path, e.g. "/message" or "/ws")
 *   TIMESTAMP           (unix epoch seconds as a float string)
 *   NONCE
 *   SHA256_HEX_OF_BODY_BYTES
 *
 * Do not "improve" this — it must byte-for-byte match what the server computes.
 */
object HmacSigner {

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun hmacSha256Hex(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun sign(
        deviceSecret: String,
        method: String,
        path: String,
        timestamp: String,
        nonce: String,
        bodyBytes: ByteArray,
    ): String {
        val bodyHash = sha256Hex(bodyBytes)
        val canonical = listOf(method.uppercase(), path, timestamp, nonce, bodyHash).joinToString("\n")
        return hmacSha256Hex(deviceSecret, canonical)
    }

    /** Unix epoch seconds as a float string, matching the server's expected format exactly. */
    fun nowTimestamp(): String = (System.currentTimeMillis() / 1000.0).toString()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
