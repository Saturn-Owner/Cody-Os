package com.cody.home.audio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * DEBUG-only overnight mic-degradation monitor (2026-09-04, revised after
 * WorkManager/JobScheduler proved unreliable on this ROM — a forced
 * `cmd jobscheduler run` was accepted but never actually invoked the
 * worker). Uses AlarmManager.setExactAndAllowWhileIdle() directly instead:
 * the lower-level, Doze-aware API this is built for, with state directly
 * inspectable via `adb shell dumpsys alarm`.
 *
 * [start] schedules the first wakeup; [MicLongTermAlarmReceiver] (manifest-
 * registered, so it survives process death) does one measurement via
 * [MicLongTermCapture] and calls [scheduleNext] for the one after that —
 * a fresh one-shot exact alarm each time, not a repeating alarm, since
 * setExactAndAllowWhileIdle() only fires once by design (that's what lets
 * it wake the device from deep Doze at all). No wakelock held by this app
 * between runs, no display kept on. Never wired into a release build —
 * see the matching no-op stub in src/release.
 */
object LongTermMicMonitor {
    private const val INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
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
     * Test-only: reschedules the SAME alarm (same PendingIntent, replacing
     * whatever was pending) to fire in [delaySeconds] instead of the full
     * 30-minute interval — for validating that a genuine, hands-off
     * AlarmManager wake actually gets real mic access, without waiting a
     * full cycle. The receiver's normal scheduleNext() call after this
     * fire puts it right back on the regular 30-minute cadence, so this
     * doesn't otherwise disturb the ongoing long-term test.
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
