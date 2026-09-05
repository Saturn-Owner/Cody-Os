package com.cody.home.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class VoiceResult {
    /** [events] kommt aus dem X-Cody-Voice-Events-Header — Batch-Zusammenfassung, kein Live-Stream. */
    data class Success(val mp3Bytes: ByteArray, val events: List<String>) : VoiceResult()
    data class Failure(val error: String, val httpCode: Int) : VoiceResult()
}

/**
 * Signierter Multipart-Upload zu `/voice`. HMAC signiert die *exakten*
 * Multipart-Body-Bytes inklusive Boundary. Deshalb wird der MultipartBody genau
 * einmal gebaut, zu Bytes serialisiert, signiert und exakt so gesendet.
 * Danach nicht neu bauen, sonst ändert sich die Boundary und die Signatur passt
 * nicht mehr.
 */
class VoiceClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS) // Audio-Upload
        .readTimeout(45, TimeUnit.SECONDS)  // STT + Cody + TTS Roundtrip
        .build()

    suspend fun sendVoice(
        credentials: DeviceCredentials,
        audioFile: File,
        requestId: String = UUID.randomUUID().toString(),
        inputFormat: String = "m4a",
    ): VoiceResult {
        if (audioFile.length() > GatewayConfig.VOICE_MAX_UPLOAD_BYTES) {
            return VoiceResult.Failure("audio_too_large", 0)
        }

        val multipart = MultipartBody.Builder(UUID.randomUUID().toString())
            .setType(MultipartBody.FORM)
            .addFormDataPart("request_id", requestId)
            .addFormDataPart("input_format", inputFormat)
            .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaType()))
            .build()

        // Exakte Bytes einmal erfassen — genau das wird gehasht und gesendet.
        val bodyBytes = Buffer().also { multipart.writeTo(it) }.readByteArray()

        val timestamp = HmacSigner.nowTimestamp()
        val nonce = UUID.randomUUID().toString()
        val signature = HmacSigner.sign(
            deviceSecret = credentials.deviceSecret,
            method = "POST",
            path = GatewayConfig.SIGNED_PATH_VOICE,
            timestamp = timestamp,
            nonce = nonce,
            bodyBytes = bodyBytes,
        )

        val requestBody = bodyBytes.toRequestBody(multipart.contentType())

        val request = Request.Builder()
            .url("${GatewayConfig.HTTPS_BASE}/voice")
            .post(requestBody)
            .addHeader("X-Cody-Device-Id", credentials.deviceId)
            .addHeader("X-Cody-Timestamp", timestamp)
            .addHeader("X-Cody-Nonce", nonce)
            .addHeader("X-Cody-Signature", signature)
            .build()

        return try {
            val response = httpClient.newCall(request).await()
            if (response.isSuccessful) {
                val mp3Bytes = response.body?.bytes() ?: ByteArray(0)
                val events = parseVoiceEventsHeader(response.header("X-Cody-Voice-Events"))
                VoiceResult.Success(mp3Bytes, events)
            } else {
                val raw = response.body?.string().orEmpty()
                VoiceResult.Failure(errorFrom(raw), response.code)
            }
        } catch (e: IOException) {
            VoiceResult.Failure("netzwerk_fehler: ${e.message}", 0)
        }
    }

    private fun parseVoiceEventsHeader(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrElse { raw.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
    }

    private fun errorFrom(raw: String): String =
        runCatching { org.json.JSONObject(raw).optString("error", "unbekannter_fehler") }.getOrDefault("unbekannter_fehler")

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }
}
