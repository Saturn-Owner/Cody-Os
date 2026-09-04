package com.cody.home.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import kotlin.math.sqrt

/**
 * DEBUG-only. The actual measurement logic for the overnight mic-degradation
 * test — plain suspend functions, no framework scheduling dependency, so it
 * can be driven by whichever mechanism (currently [MicLongTermAlarmReceiver])
 * actually wakes the device reliably on this ROM.
 *
 * One call to [runOneCycle] = one measurement point. Fixed, known-stable
 * config every time: AudioSource.MIC, 16000 Hz, mono, PCM_16BIT — so results
 * are directly comparable across the whole test instead of multiplying
 * variables. Never dumps audio to disk, only aggregate stats. Never touches
 * Gateway/VoiceRepository.
 *
 * Output: one JSON line appended per measurement to
 * <filesDir>/long_term_mic_test.jsonl. On a SILENT result, two more capture
 * attempts follow ~60s apart (inline in this same call). If all three come
 * back SILENT, a single FIRST_CONFIRMED_FAILURE marker line is written
 * (SharedPreferences flag ensures this fires only once across the whole
 * test — later runs keep logging normally even if the mic never recovers).
 */
object MicLongTermCapture {
    private const val TAG = "CodyLongTermMic"
    private const val SAMPLE_RATE_HZ = 16_000
    private const val CAPTURE_MS = 5_000L
    private const val CONFIRM_DELAY_MS = 60_000L
    private const val RESULT_PASS = "PASS"
    private const val RESULT_SILENT = "SILENT"
    const val LOG_FILE_NAME = "long_term_mic_test.jsonl"
    private const val PREFS_NAME = "cody_longterm_mic_prefs"
    private const val KEY_SEQ = "seq"
    private const val KEY_FAILURE_MARKED = "failure_marked"

    suspend fun runOneCycle(context: Context) {
        val prefs = prefs(context)
        val seq = prefs.getInt(KEY_SEQ, 0) + 1
        prefs.edit().putInt(KEY_SEQ, seq).apply()

        val primary = runCatching { capture() }.getOrElse { e ->
            appendLine(context, errorEntry(seq, e))
            return
        }
        appendLine(context, measurementEntry(seq, confirmIndex = null, result = primary))

        if (primary.result == RESULT_SILENT) {
            var allSilent = true
            for (i in 1..2) {
                delay(CONFIRM_DELAY_MS)
                val confirm = runCatching { capture() }.getOrElse { e ->
                    appendLine(context, errorEntry(seq, e, confirmIndex = i))
                    allSilent = false // inconclusive, don't claim a confirmed failure on an error
                    null
                } ?: continue
                appendLine(context, measurementEntry(seq, confirmIndex = i, result = confirm))
                if (confirm.result != RESULT_SILENT) allSilent = false
            }
            if (allSilent && !prefs.getBoolean(KEY_FAILURE_MARKED, false)) {
                prefs.edit().putBoolean(KEY_FAILURE_MARKED, true).apply()
                appendLine(context, failureMarkerEntry(seq))
            }
        }
    }

    private data class CaptureResult(
        val totalSamples: Int,
        val nonZeroSamples: Int,
        val min: Int,
        val max: Int,
        val rms: Double,
        val result: String,
    )

    private suspend fun capture(): CaptureResult {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, channelConfig, encoding)
        check(minBuf > 0) { "getMinBufferSize invalid: $minBuf" }
        val bufSize = minBuf * 4

        @Suppress("MissingPermission") // RECORD_AUDIO already granted via the real mic flow
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_HZ, channelConfig, encoding, bufSize)
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized (state=${recorder.state})" }

        val shortBuf = ShortArray(bufSize / 2)
        var totalSamples = 0
        var nonZeroSamples = 0
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        var sumSquares = 0.0

        try {
            recorder.startRecording()
            val deadline = SystemClock.elapsedRealtime() + CAPTURE_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                val n = recorder.read(shortBuf, 0, shortBuf.size)
                if (n <= 0) continue
                totalSamples += n
                for (i in 0 until n) {
                    val s = shortBuf[i].toInt()
                    if (s != 0) nonZeroSamples++
                    if (s < min) min = s
                    if (s > max) max = s
                    sumSquares += s.toDouble() * s.toDouble()
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }

        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples) else 0.0
        if (totalSamples == 0) { min = 0; max = 0 }
        // PASS requires a clear majority of real signal, not just a stray non-zero
        // sample — matches every PASS/SILENT case observed in manual testing so far,
        // where it was always either ~90%+ non-zero or exactly 0, never in between.
        val passThreshold = (totalSamples * 0.01).toInt()
        val result = if (nonZeroSamples > passThreshold) RESULT_PASS else RESULT_SILENT
        return CaptureResult(totalSamples, nonZeroSamples, min, max, rms, result)
    }

    private fun measurementEntry(seq: Int, confirmIndex: Int?, result: CaptureResult): String {
        val now = System.currentTimeMillis()
        return buildString {
            append('{')
            append("\"seq\":").append(seq).append(',')
            if (confirmIndex != null) append("\"confirm\":").append(confirmIndex).append(',')
            append("\"ts_iso\":\"").append(Instant.ofEpochMilli(now)).append("\",")
            append("\"ts_epoch_ms\":").append(now).append(',')
            append("\"uptime_s\":").append(String.format(Locale.US, "%.1f", SystemClock.elapsedRealtime() / 1000.0)).append(',')
            append("\"totalSamples\":").append(result.totalSamples).append(',')
            append("\"nonZeroSamples\":").append(result.nonZeroSamples).append(',')
            append("\"min\":").append(result.min).append(',')
            append("\"max\":").append(result.max).append(',')
            append("\"rms\":").append(String.format(Locale.US, "%.2f", result.rms)).append(',')
            append("\"result\":\"").append(result.result).append('"')
            append('}')
        }
    }

    private fun errorEntry(seq: Int, e: Throwable, confirmIndex: Int? = null): String {
        val now = System.currentTimeMillis()
        val msg = (e.message ?: e.javaClass.simpleName).replace("\"", "'")
        return buildString {
            append('{')
            append("\"seq\":").append(seq).append(',')
            if (confirmIndex != null) append("\"confirm\":").append(confirmIndex).append(',')
            append("\"ts_iso\":\"").append(Instant.ofEpochMilli(now)).append("\",")
            append("\"ts_epoch_ms\":").append(now).append(',')
            append("\"uptime_s\":").append(String.format(Locale.US, "%.1f", SystemClock.elapsedRealtime() / 1000.0)).append(',')
            append("\"result\":\"ERROR\",")
            append("\"error\":\"").append(msg).append('"')
            append('}')
        }
    }

    private fun failureMarkerEntry(seq: Int): String {
        val now = System.currentTimeMillis()
        return buildString {
            append('{')
            append("\"seq\":").append(seq).append(',')
            append("\"ts_iso\":\"").append(Instant.ofEpochMilli(now)).append("\",")
            append("\"ts_epoch_ms\":").append(now).append(',')
            append("\"uptime_s\":").append(String.format(Locale.US, "%.1f", SystemClock.elapsedRealtime() / 1000.0)).append(',')
            append("\"event\":\"FIRST_CONFIRMED_FAILURE\"")
            append('}')
        }
    }

    private fun appendLine(context: Context, json: String) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        runCatching {
            FileOutputStream(file, /* append = */ true).use { out ->
                out.write((json + "\n").toByteArray())
            }
        }.onFailure {
            Log.e(TAG, "failed to append to $LOG_FILE_NAME: ${it.message}")
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
