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
    /** [events] comes from the X-Cody-Voice-Events response header — a batch summary, not a live stream. */
    data class Success(val mp3Bytes: ByteArray, val events: List<String>) : VoiceResult()
    data class Failure(val error: String, val httpCode: Int) : VoiceResult()
}

/**
 * Signed multipart upload to `/voice`. The HMAC signs the *exact* multipart
 * body bytes (including boundary) — per the 2026-09-03 voice handoff, that
 * means: build the MultipartBody once, serialize it to bytes ourselves,
 * hash+sign those bytes, then send an identical byte-for-byte RequestBody.
 * Never rebuild the MultipartBody after computing the hash — a fresh build
 * gets a fresh random boundary and the signature would no longer match.
 */
class VoiceClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS) // uploading audio
        .readTimeout(45, TimeUnit.SECONDS)  // STT + Cody + TTS round trip
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

        // Capture the exact bytes once — this is what gets hashed AND what gets sent.
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
            VoiceResult.Failure("network_error: ${e.message}", 0)
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
        runCatching { org.json.JSONObject(raw).optString("error", "unknown_error") }.getOrDefault("unknown_error")

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
