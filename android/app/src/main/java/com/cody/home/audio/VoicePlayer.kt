package com.cody.home.audio

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.sqrt

/**
 * Spielt die MP3-Antwort des Gateways ab. Wenn [android.media.audiofx.Visualizer]
 * an die Playback-Session angehängt werden kann, bekommt [onAmplitude] mehrmals
 * pro Sekunde eine einfache RMS-Lautstärke (0f..1f). Wenn der Visualizer auf
 * diesem Build/HAL nicht unterstützt wird, übernimmt die Fallback-Animation des
 * SPEAKING-Zustands.
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
            // Kein Visualizer-Support; die eingebaute Fallback-Animation deckt das ab.
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
