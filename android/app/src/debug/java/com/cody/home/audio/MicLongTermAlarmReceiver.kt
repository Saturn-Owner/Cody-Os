package com.cody.home.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG-only, manifest-registered (see src/debug/AndroidManifest.xml) so
 * AlarmManager can wake the app even from a killed process — the whole
 * point of the overnight test surviving USB/adb disconnect and long idle
 * periods. Fired by [LongTermMicMonitor]'s exact-and-allow-while-idle
 * alarms.
 *
 * goAsync() covers the ~5s AudioRecord capture (well under the ~10s a
 * BroadcastReceiver gets before the OS considers it unresponsive); the
 * next alarm is scheduled from here, right after the measurement, so a
 * single missed/delayed run can't break the chain — a fresh receiver
 * instance and a fresh alarm every time, no long-lived component needed.
 */
class MicLongTermAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MicLongTermCapture.runOneCycle(appContext)
            } catch (e: Exception) {
                Log.e("CodyLongTermMic", "runOneCycle threw: ${e.message}", e)
            } finally {
                LongTermMicMonitor.scheduleNext(appContext)
                pendingResult.finish()
            }
        }
    }
}
