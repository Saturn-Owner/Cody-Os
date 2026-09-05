package com.cody.home.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val JSON = "application/json; charset=utf-8".toMediaType()

sealed class PairResult {
    data class Success(val credentials: DeviceCredentials) : PairResult()
    data class Failure(val error: String, val httpCode: Int) : PairResult()
}

sealed class MessageResult {
    data class Success(val text: String, val requestId: String) : MessageResult()
    data class Failure(val error: String, val httpCode: Int) : MessageResult()
}

/**
 * Schlanker REST- und WebSocket-Client für das Cody Gateway. Kennt das Wire-Protokoll
 * (Pfade, Header, HMAC-Signatur), aber keinen App-State — das ist Aufgabe von
 * [GatewayRepository]. UI-Code greift nicht direkt darauf zu.
 */
class CodyGatewayClient {

    // Eigener Client für den Socket mit längerem Read-Timeout: Die WS-Verbindung
    // soll lange offen bleiben; tote Verbindungen erkennt das Ping-Intervall unten,
    // nicht dieses Timeout.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val wsClient = httpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS) // WS-Ping/Pong auf Protokollebene; kein App-Level-Schema nötig
        .build()

    suspend fun pair(code: String, deviceName: String): PairResult {
        val body = JSONObject().apply {
            put("code", code)
            put("device_name", deviceName)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("${GatewayConfig.HTTPS_BASE}/pair")
            .post(body)
            .build()

        return try {
            val response = httpClient.newCall(request).await()
            val raw = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val json = JSONObject(raw)
                PairResult.Success(
                    DeviceCredentials(
                        deviceId = json.getString("device_id"),
                        deviceSecret = json.getString("device_secret"),
                        protocolVersion = json.optInt("protocol_version", 1),
                    )
                )
            } else {
                PairResult.Failure(errorFrom(raw), response.code)
            }
        } catch (e: IOException) {
            PairResult.Failure("netzwerk_fehler: ${e.message}", 0)
        }
    }

    suspend fun sendMessage(credentials: DeviceCredentials, text: String, requestId: String = UUID.randomUUID().toString()): MessageResult {
        val bodyJson = JSONObject().apply {
            put("request_id", requestId)
            put("text", text)
        }.toString()
        val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)

        val timestamp = HmacSigner.nowTimestamp()
        val nonce = UUID.randomUUID().toString()
        val signature = HmacSigner.sign(
            deviceSecret = credentials.deviceSecret,
            method = "POST",
            path = GatewayConfig.SIGNED_PATH_MESSAGE,
            timestamp = timestamp,
            nonce = nonce,
            bodyBytes = bodyBytes,
        )

        val request = Request.Builder()
            .url("${GatewayConfig.HTTPS_BASE}/message")
            .post(bodyBytes.toRequestBody(JSON))
            .addHeader("X-Cody-Device-Id", credentials.deviceId)
            .addHeader("X-Cody-Timestamp", timestamp)
            .addHeader("X-Cody-Nonce", nonce)
            .addHeader("X-Cody-Signature", signature)
            .build()

        return try {
            val response = httpClient.newCall(request).await()
            val raw = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val event = GatewayEvent.parse(raw)
                val answerText = event?.data?.optString("text")
                if (event != null && answerText != null) {
                    MessageResult.Success(answerText, event.requestId)
                } else {
                    MessageResult.Failure("fehlerhafte_antwort", response.code)
                }
            } else {
                MessageResult.Failure(errorFrom(raw), response.code)
            }
        } catch (e: IOException) {
            MessageResult.Failure("netzwerk_fehler: ${e.message}", 0)
        }
    }

    fun openWebSocket(listener: WebSocketListener): WebSocket {
        val request = Request.Builder().url(GatewayConfig.WSS_URL).build()
        return wsClient.newWebSocket(request, listener)
    }

    private fun errorFrom(raw: String): String =
        runCatching { JSONObject(raw).optString("error", "unbekannter_fehler") }.getOrDefault("unbekannter_fehler")

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
