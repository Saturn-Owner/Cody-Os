package com.cody.home.audio

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.sqrt

/**
 * Plays back the MP3 the Gateway returns. If [android.media.audiofx.Visualizer]
 * attaches successfully to the playback session, [onAmplitude] gets a real,
 * simple RMS-of-waveform amplitude (0f..1f) a few times a second — no FFT, no
 * frequency analysis, just "how loud is it right now". If the Visualizer can't
 * attach (not supported on this build/HAL), [onAmplitude] is simply never
 * called and the caller's own fallback animation takes over — see
 * CodyCharacter's SPEAKING state, which looks correct either way.
 */
class VoicePlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun play(mp3File: File, onAmplitude: (Float) -> Unit, onComplete: () -> Unit, onError: () -> Unit) {
        release()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(mp3File.absolutePath)
            mp.setOnPreparedListener {
                attachVisualizer(it, onAmplitude)
                it.start()
            }
            mp.setOnCompletionListener {
                onComplete()
                release()
            }
            mp.setOnErrorListener { _, _, _ ->
                onError()
                release()
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) {
            onError()
        }
    }

    private fun attachVisualizer(mp: MediaPlayer, onAmplitude: (Float) -> Unit) {
        try {
            val v = Visualizer(mp.audioSessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[0]
            val rate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        val data = waveform ?: return
                        var sumSquares = 0.0
                        for (b in data) {
                            val centered = (b.toInt() and 0xFF) - 128
                            sumSquares += centered.toDouble() * centered
                        }
                        val rms = sqrt(sumSquares / data.size)
                        val amplitude = (rms / 128.0).toFloat().coerceIn(0f, 1f)
                        mainHandler.post { onAmplitude(amplitude) }
                    }
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
                },
                rate,
                true,
                false,
            )
            v.enabled = true
            visualizer = v
        } catch (_: Exception) {
            // No Visualizer support here — the character's built-in fallback animation covers this.
        }
    }

    fun release() {
        visualizer?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
        }
        visualizer = null
        mediaPlayer?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }
}
