package com.cody.home.audio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Nur-Debug-Übernacht-Mikrofonmonitor. WorkManager/JobScheduler war auf diesem
 * ROM unzuverlässig: Ein erzwungenes `cmd jobscheduler run` wurde akzeptiert,
 * rief den Worker aber nicht wirklich auf. Stattdessen nutzt das hier direkt
 * AlarmManager.setExactAndAllowWhileIdle(), eine passendere Doze-aware API.
 *
 * [start] plant den ersten Wakeup. [MicLongTermAlarmReceiver] ist im Manifest
 * registriert, überlebt Prozessende, führt per [MicLongTermCapture] eine Messung
 * aus und plant danach den nächsten One-shot-Alarm. Zwischen Läufen hält die App
 * keinen Wakelock und kein Display an. Release-Builds nutzen den Leerlauf-Stub.
 */
object LongTermMicMonitor {
    private const val INTERVAL_MS = 30 * 60 * 1000L // 30 Minuten
    private const val ACTION = "com.cody.home.LONGTERM_MIC_ALARM"
    private const val REQUEST_CODE = 4201

    fun start(context: Context) {
        scheduleAt(context, SystemClock.elapsedRealtime() + INTERVAL_MS)
    }

    fun scheduleNext(context: Context) {
        scheduleAt(context, SystemClock.elapsedRealtime() + INTERVAL_MS)
    }

    fun stop(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    /**
     * Nur Test: Plant denselben Alarm neu, damit er in [delaySeconds] statt nach
     * dem vollen 30-Minuten-Intervall feuert. Danach stellt der Receiver wieder
     * den normalen 30-Minuten-Takt her.
     */
    fun scheduleTestAlarm(context: Context, delaySeconds: Long) {
        scheduleAt(context, SystemClock.elapsedRealtime() + delaySeconds * 1000)
    }

    private fun scheduleAt(context: Context, elapsedRealtimeMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            elapsedRealtimeMillis,
            pendingIntent(context),
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION).setClass(context, MicLongTermAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
