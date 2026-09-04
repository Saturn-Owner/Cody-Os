package com.cody.home.audio

import android.content.Context

/**
 * Release no-op counterpart to the debug-only LongTermMicMonitor
 * (app/src/debug/java/com/cody/home/audio/LongTermMicMonitor.kt).
 * Same public API, empty body — lets MainActivity call
 * LongTermMicMonitor.start()/stop() unconditionally in both build
 * variants without pulling WorkManager (a debugImplementation-only
 * dependency) into the release classpath.
 */
object LongTermMicMonitor {
    fun start(context: Context) = Unit
    fun stop(context: Context) = Unit
    fun scheduleTestAlarm(context: Context, delaySeconds: Long) = Unit
}
