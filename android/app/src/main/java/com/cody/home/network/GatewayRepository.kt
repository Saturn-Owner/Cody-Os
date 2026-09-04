package com.cody.home.network

import android.util.Log
import com.cody.home.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.UUID

/**
 * Owns the Gateway connection lifecycle: pairing, WS connect/auth, reconnect
 * with backoff, and turns raw [GatewayEvent]s into two things UI code can
 * actually consume: [connectionState] (transport-level) and [events] (every
 * parsed server event, for [com.cody.home.state.CodyStateHolder] to map onto
 * CodyUiState). Nothing in ui/ or state/ touches OkHttp or JSON directly.
 */
class GatewayRepository(private val credentialStore: CredentialStore) {

    private val client = CodyGatewayClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<GatewayConnectionState>(GatewayConnectionState.Disconnected)
    val connectionState: StateFlow<GatewayConnectionState> = _connectionState

    // replay = 8: GatewayRepository.start() runs in Activity.onCreate, before Compose has
    // subscribed to `events` — the connect -> auth -> connection.ready -> cody.state burst
    // can complete in well under 100ms, faster than composition reaches the collector.
    // Without a replay buffer that whole burst is silently dropped and the UI hangs on
    // CONNECTING forever despite a fully authenticated connection (found on real hardware,
    // 2026-09-03 — logs showed the events arriving; the UI simply never saw them).
    private val _events = MutableSharedFlow<GatewayEvent>(replay = 8, extraBufferCapacity = 32)
    val events: SharedFlow<GatewayEvent> = _events

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var manuallyStopped = false

    val isPaired: Boolean get() = credentialStore.load() != null

    fun start() {
        manuallyStopped = false
        val creds = credentialStore.load()
        if (creds == null) {
            _connectionState.value = GatewayConnectionState.NotPaired
            return
        }
        connect(creds)
    }

    fun stop() {
        manuallyStopped = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "app_stopping")
        webSocket = null
        _connectionState.value = GatewayConnectionState.Disconnected
    }

    suspend fun pairDevice(code: String, deviceName: String): PairResult {
        val result = client.pair(code, deviceName)
        if (result is PairResult.Success) {
            credentialStore.save(result.credentials)
            manuallyStopped = false
            reconnectAttempt = 0
            connect(result.credentials)
        }
        return result
    }

    suspend fun sendMessage(text: String): MessageResult {
        val creds = credentialStore.load()
            ?: return MessageResult.Failure("not_paired", 0)
        return client.sendMessage(creds, text)
    }

    fun forgetDevice() {
        stop()
        credentialStore.clear()
        _connectionState.value = GatewayConnectionState.NotPaired
    }

    private fun connect(credentials: DeviceCredentials) {
        debugLog("connect() attempt=$reconnectAttempt url=${GatewayConfig.WSS_URL}")
        reconnectJob?.cancel()
        _connectionState.value = GatewayConnectionState.Connecting

        webSocket = client.openWebSocket(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                debugLog("onOpen code=${response.code}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                debugLog("onMessage: $text")
                handleRawMessage(text, credentials, webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleRawMessage(bytes.utf8(), credentials, webSocket)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                debugLog("onClosing code=$code reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                debugLog("onClosed code=$code reason=$reason")
                if (!manuallyStopped) scheduleReconnect(credentials)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                debugLog("onFailure code=${response?.code} msg=${t.message}", t)
                if (!manuallyStopped) scheduleReconnect(credentials)
            }
        })
    }

    private fun handleRawMessage(raw: String, credentials: DeviceCredentials, ws: WebSocket) {
        val event = GatewayEvent.parse(raw)
        if (event == null) {
            debugLog("unparsed event, raw=$raw")
            return
        }
        debugLog("event type=${event.type} data=${event.data}")

        when (event.type) {
            GatewayEventType.CONNECTION_READY -> {
                val authenticated = event.data.optBoolean("authenticated", false)
                if (authenticated) {
                    reconnectAttempt = 0
                    _connectionState.value = GatewayConnectionState.Connected
                } else {
                    sendAuth(ws, credentials)
                }
            }
            GatewayEventType.CONNECTION_ERROR -> {
                val reason = event.data.optString("reason", "unknown")
                _connectionState.value = GatewayConnectionState.Error(reason)
            }
            else -> Unit
        }

        _events.tryEmit(event)
    }

    private fun sendAuth(ws: WebSocket, credentials: DeviceCredentials) {
        _connectionState.value = GatewayConnectionState.Authenticating
        val timestamp = HmacSigner.nowTimestamp()
        val nonce = UUID.randomUUID().toString()
        val bodyBytes = "{}".toByteArray(Charsets.UTF_8) // canonical JSON of an empty `data` object

        val signature = HmacSigner.sign(
            deviceSecret = credentials.deviceSecret,
            method = "WEBSOCKET",
            path = GatewayConfig.SIGNED_PATH_WS,
            timestamp = timestamp,
            nonce = nonce,
            bodyBytes = bodyBytes,
        )

        val authFrame = JSONObject().apply {
            put("type", "connection.auth")
            put("device_id", credentials.deviceId)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("signature", signature)
            put("data", JSONObject())
        }
        ws.send(authFrame.toString())
    }

    private fun scheduleReconnect(credentials: DeviceCredentials) {
        _connectionState.value = GatewayConnectionState.Disconnected
        val delayMs = backoffDelayMs(reconnectAttempt)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(BACKOFF_STEPS.size - 1)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!manuallyStopped) connect(credentials)
        }
    }

    private fun backoffDelayMs(attempt: Int): Long =
        BACKOFF_STEPS.getOrElse(attempt) { BACKOFF_STEPS.last() }

    /**
     * Debug-build-only diagnostic logging. Every call site here logs connection
     * lifecycle info and *incoming* (server -> device) event payloads — never
     * device_secret, never a signature, never the outgoing connection.auth frame
     * (that one, in [sendAuth], is deliberately never logged at all). Compiled
     * out of release builds entirely, not just filtered at runtime.
     */
    private fun debugLog(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "CodyGateway"
        // Capped exponential backoff — not an aggressive retry loop.
        val BACKOFF_STEPS = listOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
    }
}
