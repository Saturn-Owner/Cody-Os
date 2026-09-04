package com.cody.home.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * DEBUG-ONLY. Bypasses MediaRecorder entirely and reads raw PCM straight from
 * [AudioRecord] to answer one question: does *any* real, changing audio signal
 * reach the app from the audio HAL on this device at all? See VoiceRecorder's
 * KNOWN ISSUE doc comment for the MediaRecorder failure this is diagnosing.
 *
 * Never wired into a release code path — only ever invoked from MainActivity's
 * BuildConfig.DEBUG-gated broadcast receiver.
 *
 * Deliberately logs only aggregate statistics (sample counts, min/max, RMS) —
 * never raw sample values or anything secret.
 */
object AudioRecordDiagnostic {

    private const val TAG = "CodyAudioDiag"
    private const val RECORD_MS = 4_000L

    private data class Combo(
        val sourceName: String,
        val source: Int,
        val sampleRate: Int,
        val modeName: String,
        val mode: Int,
    )

    private data class ComboResult(
        val combo: Combo,
        val initState: String,
        val readCalls: Int,
        val totalSamples: Int,
        val nonZeroSamples: Int,
        val minSample: Int,
        val maxSample: Int,
        val rms: Double,
        val readErrors: List<Int>,
        val wavFile: String?,
    )

    /**
     * Runs the full source x sample-rate x mode matrix sequentially (~16 combos x 4s
     * = ~65s total) and logs one structured line per combo plus a final summary.
     * Blocking — call from a background dispatcher, never the main thread.
     */
    fun runFullMatrix(context: Context, dumpWav: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val originalMode = audioManager.mode

        val sources = listOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "DEFAULT" to MediaRecorder.AudioSource.DEFAULT,
            "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED, // API 24+; we probe live, no static support check exists
        )
        val sampleRates = listOf(16_000, 48_000)
        val modes = listOf(
            "MODE_NORMAL" to AudioManager.MODE_NORMAL,
            "MODE_IN_COMMUNICATION" to AudioManager.MODE_IN_COMMUNICATION,
        )

        Log.i(TAG, "=== matrix starting: ${sources.size} sources x ${sampleRates.size} rates x ${modes.size} modes, ${RECORD_MS}ms each ===")

        val results = mutableListOf<ComboResult>()
        try {
            for ((modeName, mode) in modes) {
                audioManager.mode = mode
                for ((sourceName, source) in sources) {
                    for (rate in sampleRates) {
                        val combo = Combo(sourceName, source, rate, modeName, mode)
                        val result = runSingle(context, combo, dumpWav)
                        results += result
                        logResult(result)
                    }
                }
            }
        } finally {
            audioManager.mode = originalMode
        }

        val realSignalCombos = results.filter { it.nonZeroSamples > 0 && it.rms > 1.0 }
        Log.i(TAG, "=== matrix complete: ${results.size} combos tested, ${realSignalCombos.size} showed real non-zero/RMS>1.0 signal ===")
        if (realSignalCombos.isNotEmpty()) {
            Log.i(TAG, "=== combos with real signal: ${realSignalCombos.map { "${it.combo.sourceName}/${it.combo.sampleRate}Hz/${it.combo.modeName}" }} ===")
        }
    }

    private fun runSingle(context: Context, combo: Combo, dumpWav: Boolean): ComboResult {
        val label = "${combo.sourceName}/${combo.sampleRate}Hz/${combo.modeName}"
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBufSize = AudioRecord.getMinBufferSize(combo.sampleRate, channelConfig, encoding)
        if (minBufSize <= 0) {
            Log.w(TAG, "[$label] getMinBufferSize invalid: $minBufSize — skipping")
            return ComboResult(combo, "INVALID_MIN_BUFFER($minBufSize)", 0, 0, 0, 0, 0, 0.0, emptyList(), null)
        }
        val bufSize = minBufSize * 4

        val recorder = try {
            @Suppress("MissingPermission") // RECORD_AUDIO already granted via the real mic flow before this diagnostic is triggered
            AudioRecord(combo.source, combo.sampleRate, channelConfig, encoding, bufSize)
        } catch (e: Exception) {
            Log.w(TAG, "[$label] AudioRecord() threw: ${e.javaClass.simpleName}: ${e.message}")
            return ComboResult(combo, "CONSTRUCTOR_EXCEPTION(${e.javaClass.simpleName})", 0, 0, 0, 0, 0, 0.0, emptyList(), null)
        }

        val initState = when (recorder.state) {
            AudioRecord.STATE_INITIALIZED -> "STATE_INITIALIZED"
            AudioRecord.STATE_UNINITIALIZED -> "STATE_UNINITIALIZED"
            else -> "UNKNOWN(${recorder.state})"
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return ComboResult(combo, initState, 0, 0, 0, 0, 0, 0.0, emptyList(), null)
        }

        val shortBuf = ShortArray(bufSize / 2)
        var readCalls = 0
        var totalSamples = 0
        var nonZeroSamples = 0
        var minSample = Int.MAX_VALUE
        var maxSample = Int.MIN_VALUE
        var sumSquares = 0.0
        val readErrors = mutableListOf<Int>()
        var pcmFile: File? = null
        val pcmOut = if (dumpWav) {
            pcmFile = File(context.cacheDir, "diag_${combo.sourceName}_${combo.sampleRate}_${combo.modeName}.pcmtmp")
            pcmFile.outputStream().buffered()
        } else null

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "[$label] startRecording() did not reach RECORDSTATE_RECORDING (state=${recorder.recordingState})")
            }
            val deadline = System.currentTimeMillis() + RECORD_MS
            while (System.currentTimeMillis() < deadline) {
                val n = recorder.read(shortBuf, 0, shortBuf.size)
                readCalls++
                if (n < 0) {
                    readErrors += n
                    continue
                }
                totalSamples += n
                for (i in 0 until n) {
                    val s = shortBuf[i].toInt()
                    if (s != 0) nonZeroSamples++
                    if (s < minSample) minSample = s
                    if (s > maxSample) maxSample = s
                    sumSquares += s.toDouble() * s.toDouble()
                }
                pcmOut?.let { out ->
                    val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n) bb.putShort(shortBuf[i])
                    out.write(bb.array())
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            runCatching { pcmOut?.close() }
        }

        val rms = if (totalSamples > 0) sqrt(sumSquares / totalSamples) else 0.0
        if (totalSamples == 0) {
            minSample = 0
            maxSample = 0
        }

        val wavPath = if (dumpWav && pcmFile != null && totalSamples > 0) {
            val wavFile = File(context.cacheDir, "diag_${combo.sourceName}_${combo.sampleRate}_${combo.modeName}.wav")
            writeWav(pcmFile, wavFile, combo.sampleRate)
            pcmFile.delete()
            wavFile.absolutePath
        } else {
            pcmFile?.delete()
            null
        }

        return ComboResult(combo, initState, readCalls, totalSamples, nonZeroSamples, minSample, maxSample, rms, readErrors, wavPath)
    }

    private fun writeWav(pcmFile: File, outFile: File, sampleRate: Int) {
        val pcmData = pcmFile.readBytes()
        val dataSize = pcmData.size
        RandomAccessFile(outFile, "rw").use { out ->
            out.setLength(0)
            fun writeLE(v: Int, bytes: Int) {
                for (i in 0 until bytes) out.write((v shr (8 * i)) and 0xFF)
            }
            out.writeBytes("RIFF"); writeLE(36 + dataSize, 4); out.writeBytes("WAVE")
            out.writeBytes("fmt "); writeLE(16, 4); writeLE(1, 2) // PCM
            writeLE(1, 2) // mono
            writeLE(sampleRate, 4)
            writeLE(sampleRate * 2, 4) // byte rate (16-bit mono)
            writeLE(2, 2) // block align
            writeLE(16, 2) // bits per sample
            out.writeBytes("data"); writeLE(dataSize, 4)
            out.write(pcmData)
        }
    }

    private fun logResult(r: ComboResult) {
        Log.i(
            TAG,
            "[${r.combo.sourceName}/${r.combo.sampleRate}Hz/${r.combo.modeName}] " +
                "init=${r.initState} reads=${r.readCalls} samples=${r.totalSamples} nonZero=${r.nonZeroSamples} " +
                "min=${r.minSample} max=${r.maxSample} rms=${"%.2f".format(r.rms)} errors=${r.readErrors} wav=${r.wavFile ?: "none"}",
        )
    }
}
