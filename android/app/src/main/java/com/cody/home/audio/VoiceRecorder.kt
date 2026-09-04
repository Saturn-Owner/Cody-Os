package com.cody.home.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.cody.home.BuildConfig
import java.io.File

private const val MAX_RECORDING_MS = 30_000 // sane upper bound — nobody needs a 15-minute voice note here
private const val SAMPLE_RATE_HZ = 16_000   // mono voice, not music — keeps the m4a small under the 15 MiB /voice cap
private const val BITRATE_BPS = 64_000

/**
 * Records mono AAC-in-MP4 (.m4a) via [MediaRecorder] — the highest-level API that
 * still gives us the exact container/codec the Gateway asks for, without hand-rolling
 * an encoder via MediaCodec. One instance is single-use: start() once, stop() or
 * cancel() once, then discard it.
 *
 * KNOWN ISSUE (2026-09-02, real hardware): on this Echo Show 5 / LineageOS "checkers"
 * port, MediaRecorder.stop() reliably throws RuntimeException("stop failed") with an
 * identical 3354-byte header-only output file — reproduced across AudioSource.MIC,
 * VOICE_RECOGNITION and DEFAULT, and across MPEG_4/AAC as well as the much more
 * primitive THREE_GPP/AMR_NB. Since the failure is identical regardless of container
 * or encoder, the problem sits upstream of MediaRecorder's encode/mux stage — no real
 * PCM samples appear to reach it from the audio HAL at all, consistent with this
 * port's already-documented partial mic-array support. Not resolved from app code;
 * see the Voice V1 report for suggested next steps (e.g. probing raw AudioRecord).
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** Starts recording to a fresh cache file. [onMaxDurationReached] fires if the cap above kicks in. */
    fun start(onMaxDurationReached: () -> Unit): File {
        check(recorder == null) { "VoiceRecorder is single-use — create a new instance per recording." }

        val file = File(context.cacheDir, "cody_voice_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION") // MediaRecorder(Context) needs API 31; this app's minSdk/targetSdk is fixed at 30.
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
            debugLog("started ok, file=${file.absolutePath}")
        }
        recorder = r
        outputFile = file
        return file
    }

    /** Stops cleanly and returns the recorded file, or null if the recording was too short/invalid. */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outputFile
        debugLog("stop() called, file exists before stop=${file?.exists()} size=${file?.length()}")
        return try {
            r.stop()
            r.release()
            recorder = null
            debugLog("stop() ok, file exists=${file?.exists()} size=${file?.length()}")
            file
        } catch (e: RuntimeException) {
            // MediaRecorder.stop() throws if stop() is called too soon after start() with no data captured
            // — or, as found on this hardware, if the HAL never delivered real samples at all.
            if (BuildConfig.DEBUG) Log.e(TAG, "stop() threw: ${e.javaClass.simpleName}: ${e.message}", e)
            r.release()
            recorder = null
            file?.delete()
            null
        }
    }

    /** Aborts and deletes the partial file — used when the user backs out or the app is torn down mid-recording. */
    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // fine — we're discarding the file either way
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
