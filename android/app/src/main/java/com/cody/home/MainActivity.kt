package com.cody.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.cody.home.audio.VoicePlayer
import com.cody.home.audio.VoiceRecorder
import com.cody.home.character.CharacterController
import com.cody.home.network.CredentialStore
import com.cody.home.network.GatewayConnectionState
import com.cody.home.network.GatewayRepository
import com.cody.home.network.GatewayStateMapper
import com.cody.home.network.MessageResult
import com.cody.home.network.PairResult
import com.cody.home.network.VoiceRepository
import com.cody.home.network.VoiceResult
import com.cody.home.state.CodyState
import com.cody.home.state.CodyStateHolder
import com.cody.home.ui.AmbientScreen
import com.cody.home.ui.DashboardScreen
import com.cody.home.ui.DevStatePanel
import com.cody.home.ui.PairingScreen
import com.cody.home.ui.theme.CodyHomeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Cody Home — V1-Dashboard mit echter Verbindung zum Cody Home Gateway.
 *
 * Ambient-/Standby-Verhalten:
 * Der Bildschirm bleibt dauerhaft an (FLAG_KEEP_SCREEN_ON). Nach
 * [AMBIENT_TIMEOUT_MS] ohne Touch wechselt die UI in eine dunkle, reduzierte
 * Ambient-Ansicht; jeder Touch weckt sie sofort wieder.
 *
 * Hinweis zum Umgebungslichtsensor:
 * TYPE_LIGHT wurde getestet, aber die Rohwerte dieses LineageOS-Ports schwanken
 * unabhängig von der echten Raumhelligkeit nur zwischen 1.0 und 2.0. Das wirkt
 * wie ein HAL-/Kalibrierungsproblem und ist nicht sinnvoll aus App-Code lösbar.
 */

private const val AMBIENT_TIMEOUT_MS = 120_000L // 2 Minuten ohne Touch
private const val NORMAL_BRIGHTNESS = 0.45f
private const val AMBIENT_BRIGHTNESS = 0.04f

private const val DEV_PANEL_TAPS_REQUIRED = 5
private const val DEV_PANEL_TAP_WINDOW_MS = 3_000L

class MainActivity : ComponentActivity() {

    private val lastInteractionMillis = mutableLongStateOf(System.currentTimeMillis())
    private val stateHolder = CodyStateHolder()
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var voiceRepository: VoiceRepository
    private var debugStateReceiver: BroadcastReceiver? = null
    // Absichtlich auf Activity-Ebene gehalten: Ein Coroutine-lokaler VoicePlayer
    // wurde während der Wiedergabe finalisiert, sobald die startende Coroutine endete.
    // Betrifft nur Debug-Fixtures; der echte Mikrofon-Flow hält den Player in VoiceSession.
    private var debugVoicePlayer: VoicePlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        val credentialStore = CredentialStore(applicationContext)
        gatewayRepository = GatewayRepository(credentialStore)
        gatewayRepository.start()
        voiceRepository = VoiceRepository(credentialStore)
        if (BuildConfig.DEBUG) registerDebugStateReceiver()

        setContent {
            CodyHomeTheme {
                val isAmbient = rememberAmbientState(lastInteractionMillis)
                ApplyWindowBrightness(isAmbient)
                CodyHomeRoot(
                    isAmbient = isAmbient,
                    stateHolder = stateHolder,
                    gateway = gatewayRepository,
                    voice = voiceRepository,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Sees every touch before Compose consumes it — used purely as an idle-timer reset. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            lastInteractionMillis.longValue = System.currentTimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** Dimmt nur dieses Fenster — keine WRITE_SETTINGS-Berechtigung nötig. */
    fun setWindowBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    /**
     * Nur Debug: steuert [stateHolder] und Pairing direkt aus der Shell, weil
     * die 5-Tap-Geste und Textfeld-Taps auf diesem LineageOS-Build nicht
     * zuverlässig per `adb shell input tap` ausgelöst werden können. In Release
     * nie registriert.
     *
     * Nutzung:
     *   adb shell am broadcast -a com.cody.home.DEBUG_SET_STATE --es state THINKING
     *   adb shell am broadcast -a com.cody.home.DEBUG_PAIR --es code 123456 --es device_name "Cody Home Echo"
     */
    private fun registerDebugStateReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.cody.home.DEBUG_SET_STATE" -> {
                        val name = intent.getStringExtra("state") ?: return
                        val state = runCatching { CodyState.valueOf(name) }.getOrNull() ?: return
                        applyDevState(stateHolder, state)
                    }
                    "com.cody.home.DEBUG_PAIR" -> {
                        val code = intent.getStringExtra("code") ?: return
                        val deviceName = intent.getStringExtra("device_name") ?: "Cody Home Echo"
                        lifecycleScope.launch { gatewayRepository.pairDevice(code, deviceName) }
                    }
                    "com.cody.home.DEBUG_SEND_VOICE_FIXTURE" -> {
                        // Mikrofonfreier Pipeline-Test: prüft Upload/HMAC, STT, Cody,
                        // TTS und Wiedergabe mit vorab aufgenommener Datei statt MediaRecorder.
                        // Vollständig getrennt vom normalen Mikrofonpfad.
                        val fixture = java.io.File(cacheDir, "voice_fixture.m4a")
                        if (!fixture.exists()) {
                            android.util.Log.e("CodyVoiceFixture", "fixture-datei fehlt: ${fixture.absolutePath}")
                            return
                        }
                        stateHolder.setState(CodyState.THINKING, statusText = "Verarbeite (Fixture)...")
                        lifecycleScope.launch {
                            when (val result = voiceRepository.sendVoice(fixture)) {
                                is com.cody.home.network.VoiceResult.Success -> {
                                    android.util.Log.d(
                                        "CodyVoiceFixture",
                                        "upload+STT+Cody+TTS ok, mp3Bytes=${result.mp3Bytes.size}, events=${result.events}"
                                    )
                                    val mp3File = java.io.File(cacheDir, "voice_fixture_reply.mp3")
                                    mp3File.writeBytes(result.mp3Bytes)
                                    stateHolder.setState(CodyState.SPEAKING, statusText = "Cody antwortet (Fixture)...")
                                    debugVoicePlayer?.release()
                                    val player = com.cody.home.audio.VoicePlayer()
                                    debugVoicePlayer = player
                                    player.play(
                                        mp3File = mp3File,
                                        onAmplitude = { amp ->
                                            android.util.Log.d("CodyVoiceFixture", "visualizer amplitude=$amp")
                                        },
                                        onComplete = {
                                            mp3File.delete()
                                            player.release()
                                            debugVoicePlayer = null
                                            stateHolder.setState(CodyState.SUCCESS)
                                        },
                                        onError = {
                                            mp3File.delete()
                                            player.release()
                                            debugVoicePlayer = null
                                            stateHolder.setState(CodyState.ERROR, message = "Fixture-Wiedergabe fehlgeschlagen.")
                                        },
                                    )
                                }
                                is com.cody.home.network.VoiceResult.Failure -> {
                                    android.util.Log.e(
                                        "CodyVoiceFixture",
                                        "sendVoice fehler: httpCode=${result.httpCode} error=${result.error}"
                                    )
                                    stateHolder.setState(CodyState.ERROR, message = result.error)
                                }
                            }
                        }
                    }
                    "com.cody.home.DEBUG_RUN_AUDIORECORD_DIAG" -> {
                        // Direkte AudioRecord-Diagnose (2026-09-03): umgeht MediaRecorder
                        // vollständig — siehe den Doc-Kommentar von AudioRecordDiagnostic.
                        // Läuft auf einem Hintergrund-Dispatcher, weil die vollständige
                        // Source-x-Sample-Rate-x-Mode-Matrix ca. 65s blockiert; berührt nie Compose-State.
                        android.util.Log.i("CodyAudioDiag", "Matrix über Debug-Broadcast ausgelöst")
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.cody.home.audio.AudioRecordDiagnostic.runFullMatrix(applicationContext, dumpWav = true)
                        }
                    }
                    "com.cody.home.DEBUG_START_LONGTERM_MIC_MONITOR" -> {
                        // Übernacht-Mikrofon-Degradationstest (2026-09-04): plant
                        // per LongTermMicMonitor ca. alle 30 Minuten eine Messung.
                        // Überlebt App-/Prozessende, Ambient-Modus, Deep-Sleep und
                        // USB-/adb-Trennung; hält zwischen Läufen keinen eigenen Wakelock.
                        android.util.Log.i("CodyLongTermMic", "Übernacht-Monitor wird gestartet")
                        com.cody.home.audio.LongTermMicMonitor.start(applicationContext)
                    }
                    "com.cody.home.DEBUG_STOP_LONGTERM_MIC_MONITOR" -> {
                        android.util.Log.i("CodyLongTermMic", "Übernacht-Monitor wird gestoppt")
                        com.cody.home.audio.LongTermMicMonitor.stop(applicationContext)
                    }
                    "com.cody.home.DEBUG_TEST_LONGTERM_MIC_ALARM_SOON" -> {
                        // Nur Validierung: feuert die nächste Messung in ca. 60s über einen echten
                        // AlarmManager-Wakeup statt per manuellem Broadcast. So prüfen wir, dass
                        // Android den Hintergrund-Mikrofonzugriff nicht blockiert, bevor wir dem
                        // Übernachtlauf vertrauen. Danach geht es automatisch in den normalen
                        // 30-Minuten-Takt zurück.
                        val delaySeconds = intent.getLongExtra("delay_seconds", 60L)
                        android.util.Log.i("CodyLongTermMic", "Test-Alarm in ${delaySeconds}s wird geplant")
                        com.cody.home.audio.LongTermMicMonitor.scheduleTestAlarm(applicationContext, delaySeconds)
                    }
                    "com.cody.home.DEBUG_SEND_MESSAGE" -> {
                        val text = intent.getStringExtra("text") ?: return
                        lifecycleScope.launch {
                            when (val result = gatewayRepository.sendMessage(text)) {
                                is com.cody.home.network.MessageResult.Success ->
                                    stateHolder.setMessage(result.text)
                                is com.cody.home.network.MessageResult.Failure ->
                                    stateHolder.setState(CodyState.ERROR, message = result.error)
                            }
                        }
                    }
                }
            }
        }
        // Absichtlich exportiert: Damit können nur `adb shell am broadcast` oder eine
        // andere lokale App die Demo-State-Machine umschalten. Der Receiver wird
        // ohnehin ausschließlich in BuildConfig.DEBUG registriert.
        val filter = IntentFilter().apply {
            addAction("com.cody.home.DEBUG_SET_STATE")
            addAction("com.cody.home.DEBUG_PAIR")
            addAction("com.cody.home.DEBUG_SEND_MESSAGE")
            addAction("com.cody.home.DEBUG_SEND_VOICE_FIXTURE")
            addAction("com.cody.home.DEBUG_RUN_AUDIORECORD_DIAG")
            addAction("com.cody.home.DEBUG_START_LONGTERM_MIC_MONITOR")
            addAction("com.cody.home.DEBUG_STOP_LONGTERM_MIC_MONITOR")
            addAction("com.cody.home.DEBUG_TEST_LONGTERM_MIC_ALARM_SOON")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        debugStateReceiver = receiver
    }

    override fun onDestroy() {
        debugStateReceiver?.let { unregisterReceiver(it) }
        debugVoicePlayer?.release()
        gatewayRepository.stop()
        super.onDestroy()
    }
}

/** Wird vom Dev-Panel und vom Debug-Broadcast-Receiver geteilt, damit beide synchron bleiben. */
private fun applyDevState(stateHolder: CodyStateHolder, state: CodyState) {
    when (state) {
        CodyState.WORKING -> stateHolder.simulateWorkingTask("Beispiel-Aufgabe läuft")
        CodyState.APPROVAL_REQUIRED -> stateHolder.setState(state, message = "sudo systemctl restart caddy")
        CodyState.SPEAKING -> stateHolder.setState(state, message = "Alles läuft normal.")
        CodyState.ERROR -> stateHolder.setState(state, message = "Verbindung zum Tool fehlgeschlagen.")
        else -> stateHolder.setState(state)
    }
}

/**
 * Startet eine neue Aufnahme. Beim eingebauten Maximaldauer-Limit (siehe [VoiceRecorder])
 * wird automatisch gestoppt und gesendet — derselbe Pfad wie bei einem manuellen zweiten Tippen.
 */
private fun startVoiceRecording(
    context: Context,
    voiceSession: VoiceSession,
    stateHolder: CodyStateHolder,
    voice: VoiceRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onRecordingStateChanged: (Boolean) -> Unit,
    onAmplitude: (Float) -> Unit,
) {
    try {
        val recorder = VoiceRecorder(context)
        voiceSession.recorder = recorder
        recorder.start(onMaxDurationReached = {
            onRecordingStateChanged(false)
            stopVoiceRecordingAndSend(context, voiceSession, stateHolder, voice, scope, onAmplitude)
        })
        stateHolder.setState(CodyState.LISTENING, statusText = "Höre zu...")
        onRecordingStateChanged(true)
    } catch (e: Exception) {
        voiceSession.recorder = null
        stateHolder.setState(CodyState.ERROR, message = "Aufnahme konnte nicht gestartet werden.")
    }
}

/** Stoppt die aktuelle Aufnahme, lädt sie hoch, spielt die Antwort ab und räumt beide Temp-Dateien auf. */
private fun stopVoiceRecordingAndSend(
    context: Context,
    voiceSession: VoiceSession,
    stateHolder: CodyStateHolder,
    voice: VoiceRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onAmplitude: (Float) -> Unit,
) {
    val recorder = voiceSession.recorder ?: return
    voiceSession.recorder = null
    val audioFile = recorder.stop()
    if (audioFile == null) {
        stateHolder.setState(CodyState.ERROR, message = "Keine Aufnahme erkannt.")
        return
    }

    stateHolder.setState(CodyState.THINKING, statusText = "Verarbeite...")
    scope.launch {
        when (val result = voice.sendVoice(audioFile)) {
            is VoiceResult.Success -> {
                audioFile.delete()
                val mp3File = File(context.cacheDir, "cody_reply_${System.currentTimeMillis()}.mp3")
                mp3File.writeBytes(result.mp3Bytes)
                stateHolder.setState(CodyState.SPEAKING, statusText = "Cody antwortet...")
                voiceSession.player.play(
                    mp3File = mp3File,
                    onAmplitude = onAmplitude,
                    onComplete = {
                        mp3File.delete()
                        onAmplitude(0f)
                        stateHolder.setState(CodyState.SUCCESS)
                    },
                    onError = {
                        mp3File.delete()
                        onAmplitude(0f)
                        stateHolder.setState(CodyState.ERROR, message = "Wiedergabe fehlgeschlagen.")
                    },
                )
            }
            is VoiceResult.Failure -> {
                audioFile.delete()
                stateHolder.setState(CodyState.ERROR, message = result.error)
            }
        }
    }
}

/** Ambient = true nach [AMBIENT_TIMEOUT_MS] ohne Touch; jede Berührung weckt sofort. */
@Composable
private fun rememberAmbientState(lastInteraction: State<Long>): Boolean {
    var isAmbient by remember { mutableStateOf(false) }
    val lastInteractionValue by lastInteraction

    LaunchedEffect(lastInteractionValue) { isAmbient = false }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (System.currentTimeMillis() - lastInteraction.value >= AMBIENT_TIMEOUT_MS) {
                isAmbient = true
            }
        }
    }
    return isAmbient
}

@Composable
private fun ApplyWindowBrightness(isAmbient: Boolean) {
    val context = LocalContext.current
    DisposableEffect(isAmbient) {
        (context as? MainActivity)?.setWindowBrightness(if (isAmbient) AMBIENT_BRIGHTNESS else NORMAL_BRIGHTNESS)
        onDispose { }
    }
}

/** Hält Voice-Objekte außerhalb des Compose-State über Recompositions hinweg:
 *  einen frischen [VoiceRecorder] pro Aufnahme (Single-Use) und einen wiederverwendeten [VoicePlayer]. */
private class VoiceSession {
    var recorder: VoiceRecorder? = null
    val player = VoicePlayer()
}

@Composable
private fun CodyHomeRoot(
    isAmbient: Boolean,
    stateHolder: CodyStateHolder,
    gateway: GatewayRepository,
    voice: VoiceRepository,
) {
    val connectionState by gateway.connectionState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isRecording by remember { mutableStateOf(false) }
    var liveAmplitude by remember { mutableFloatStateOf(0f) }
    val voiceSession = remember { VoiceSession() }

    DisposableEffect(Unit) {
        onDispose {
            voiceSession.recorder?.cancel()
            voiceSession.player.release()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording(
                context = context, voiceSession = voiceSession, stateHolder = stateHolder,
                voice = voice, scope = scope,
                onRecordingStateChanged = { isRecording = it },
                onAmplitude = { liveAmplitude = it },
            )
        } else {
            stateHolder.setState(CodyState.ERROR, message = "Mikrofon-Berechtigung verweigert.")
        }
    }

    fun onMicTap() {
        if (isRecording) {
            isRecording = false
            stopVoiceRecordingAndSend(
                context = context,
                voiceSession = voiceSession,
                stateHolder = stateHolder,
                voice = voice,
                scope = scope,
                onAmplitude = { liveAmplitude = it },
            )
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startVoiceRecording(
                context = context, voiceSession = voiceSession, stateHolder = stateHolder,
                voice = voice, scope = scope,
                onRecordingStateChanged = { isRecording = it },
                onAmplitude = { liveAmplitude = it },
            )
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Transportzustand -> CodyUiState. Der Server sendet nach erfolgreicher Auth
    // selbst das erste echte cody.state-Event; Connected erzwingt hier kein IDLE.
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is GatewayConnectionState.Connecting, is GatewayConnectionState.Authenticating ->
                stateHolder.setState(CodyState.CONNECTING)
            is GatewayConnectionState.Disconnected, is GatewayConnectionState.Error ->
                stateHolder.setState(CodyState.OFFLINE)
            else -> Unit
        }
    }

    // Jedes geparste Gateway-Event -> CodyUiState, über den einen Mapper, dem diese Übersetzung gehört.
    LaunchedEffect(Unit) {
        gateway.events.collect { event -> GatewayStateMapper.apply(event, stateHolder) }
    }

    // SUCCESS ist transient — automatisch zu IDLE zurückfallen.
    LaunchedEffect(stateHolder.uiState.state) {
        if (stateHolder.uiState.state == CodyState.SUCCESS) {
            delay(2_500)
            if (stateHolder.uiState.state == CodyState.SUCCESS) {
                stateHolder.setState(CodyState.IDLE)
            }
        }
    }

    var showDevPanel by remember { mutableStateOf(false) }
    val tapTimestamps = remember { mutableStateListOf<Long>() }
    val character = CharacterController.derive(stateHolder.uiState, isAmbient, liveAudioAmplitude = liveAmplitude)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (connectionState is GatewayConnectionState.NotPaired) {
            var isPairing by remember { mutableStateOf(false) }
            var pairError by remember { mutableStateOf<String?>(null) }
            PairingScreen(
                isPairing = isPairing,
                errorMessage = pairError,
                onSubmit = { code, deviceName ->
                    isPairing = true
                    pairError = null
                    scope.launch {
                        when (val result = gateway.pairDevice(code, deviceName)) {
                            is PairResult.Success -> { /* connectionState flow updates itself */ }
                            is PairResult.Failure -> pairError = result.error
                        }
                        isPairing = false
                    }
                },
            )
            return@Surface
        }

        val devTapModifier = if (BuildConfig.DEBUG) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    tapTimestamps.add(now)
                    tapTimestamps.removeAll { now - it > DEV_PANEL_TAP_WINDOW_MS }
                    if (tapTimestamps.size >= DEV_PANEL_TAPS_REQUIRED) {
                        tapTimestamps.clear()
                        showDevPanel = true
                    }
                })
            }
        } else Modifier

        AnimatedContent(
            targetState = isAmbient,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
            label = "ambient-transition",
            modifier = Modifier.fillMaxSize(),
        ) { ambient ->
            if (ambient) {
                AmbientScreen(character = character)
            } else {
                DashboardScreen(
                    uiState = stateHolder.uiState,
                    character = character,
                    onApprove = { stateHolder.setState(CodyState.SUCCESS, statusText = "Genehmigt.") },
                    onDeny = { stateHolder.setState(CodyState.IDLE, statusText = "Abgelehnt.") },
                    characterTapModifier = devTapModifier,
                    isRecording = isRecording,
                    onMicTap = { onMicTap() },
                )
            }
        }

        if (showDevPanel && BuildConfig.DEBUG) {
            DevStatePanel(
                onSelect = { state -> applyDevState(stateHolder, state) },
                onSendMessage = { text ->
                    scope.launch {
                            // Das Gateway pusht während der Arbeit zusätzlich cody.state über WS;
                            // die REST-Antwort hier enthält den eigentlichen Antworttext direkt.
                        when (val result = gateway.sendMessage(text)) {
                            is MessageResult.Success -> stateHolder.setMessage(result.text)
                            is MessageResult.Failure -> stateHolder.setState(CodyState.ERROR, message = result.error)
                        }
                    }
                },
                onForgetDevice = { gateway.forgetDevice() },
                onClose = { showDevPanel = false },
            )
        }
    }
}
