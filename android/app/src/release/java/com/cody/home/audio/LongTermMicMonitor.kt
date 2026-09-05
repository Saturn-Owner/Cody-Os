package com.cody.home.audio

import android.content.Context

/**
 * Release-Leerlauf-Gegenstück zur Nur-Debug-Implementierung von LongTermMicMonitor.
 * Gleiche öffentliche API, leerer Body: So kann MainActivity start()/stop()
 * in beiden Build-Varianten aufrufen, ohne WorkManager in den Release-Classpath
 * zu ziehen.
 */
object LongTermMicMonitor {
    fun start(context: Context) = Unit
    fun stop(context: Context) = Unit
    fun scheduleTestAlarm(context: Context, delaySeconds: Long) = Unit
}
