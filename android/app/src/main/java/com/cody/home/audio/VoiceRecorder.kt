package com.cody.home.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.cody.home.BuildConfig
import java.io.File

private const val MAX_RECORDING_MS = 30_000 // sinnvolle Obergrenze — keine 15-Minuten-Sprachnotizen
private const val SAMPLE_RATE_HZ = 16_000   // Mono-Sprache, keine Musik — hält m4a unter dem /voice-Limit
private const val BITRATE_BPS = 64_000

/**
 * Nimmt Mono-AAC-in-MP4 (.m4a) über [MediaRecorder] auf — die höchste API-Ebene,
 * die trotzdem genau Container/Codec liefert, die das Gateway erwartet. Eine
 * Instanz ist Einweg: einmal start(), einmal stop() oder cancel(), dann verwerfen.
 *
 * BEKANNTES PROBLEM (2026-09-02, echte Hardware): Auf diesem Echo Show 5 /
 * LineageOS-`checkers`-Port wirft MediaRecorder.stop() zuverlässig
 * RuntimeException("stop failed") und erzeugt nur eine identische 3354-Byte-
 * Headerdatei. Das tritt unabhängig von AudioSource, Container und Encoder auf;
 * vermutlich erreicht kein echtes PCM-Signal die App aus der Audio-HAL.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Startet die Aufnahme in eine frische Cache-Datei. [onMaxDurationReached] feuert bei Zeitlimit. */
    fun start(onMaxDurationReached: () -> Unit): File {
        check(recorder == null) { "VoiceRecorder ist Einweg — pro Aufnahme eine neue Instanz erzeugen." }

        val file = File(context.cacheDir, "cody_voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION") // MediaRecorder(Context) braucht API 31; minSdk/targetSdk bleibt bewusst 30.
        val r = MediaRecorder()
        r.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(SAMPLE_RATE_HZ)
            setAudioEncodingBitRate(BITRATE_BPS)
            setOutputFile(file.absolutePath)
            setMaxDuration(MAX_RECORDING_MS)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxDurationReached()
            }
            debugLog("prepare()")
            prepare()
            debugLog("start()")
            start()
            debugLog("gestartet ok, datei=${file.absolutePath}")
        }
        recorder = r
        outputFile = file
        return file
    }

    /** Stoppt sauber und liefert die Datei, oder null bei zu kurzer/ungültiger Aufnahme. */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outputFile
        debugLog("stop() aufgerufen, datei_vorher_vorhanden=${file?.exists()} groesse=${file?.length()}")
        return try {
            r.stop()
            r.release()
            recorder = null
            debugLog("stop() ok, datei_vorhanden=${file?.exists()} groesse=${file?.length()}")
            file
        } catch (e: RuntimeException) {
            // MediaRecorder.stop() wirft, wenn zu früh gestoppt wurde oder die HAL keine Samples liefert.
            if (BuildConfig.DEBUG) Log.e(TAG, "stop() fehler: ${e.javaClass.simpleName}: ${e.message}", e)
            r.release()
            recorder = null
            file?.delete()
            null
        }
    }

    /** Bricht ab und löscht die Teil-Datei, etwa beim Zurückgehen oder App-Abbau. */
    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // in Ordnung — die Datei wird ohnehin verworfen
        }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "CodyVoiceRecorder"
    }
}
