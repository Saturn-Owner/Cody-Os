package com.cody.home.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementiert exakt das HMAC-SHA256-Schema des Cody Home Gateways.
 * Kanonischer String, in Reihenfolge, per Newline getrennt, ohne abschließende
 * Newline:
 *   METHOD_UPPERCASE
 *   PATH               (interner/signierter Pfad, z. B. "/message" oder "/ws")
 *   TIMESTAMP           (Unix-Epoch-Sekunden als Float-String)
 *   NONCE
 *   SHA256_HEX_OF_BODY_BYTES
 *
 * Nicht „verbessern“: Das muss bytegenau zur Serverberechnung passen.
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

    /** Unix-Epoch-Sekunden als Float-String, exakt im vom Server erwarteten Format. */
    fun nowTimestamp(): String = (System.currentTimeMillis() / 1000.0).toString()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
