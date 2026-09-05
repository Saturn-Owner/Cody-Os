package com.cody.home.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nur Debug, im Manifest registriert, damit AlarmManager die App auch aus einem
 * beendeten Prozess wecken kann. Genau das braucht der Übernacht-Test über
 * USB-/adb-Trennung und lange Idle-Phasen.
 *
 * goAsync() deckt die ca. 5s AudioRecord-Messung ab. Der nächste Alarm wird
 * direkt nach der Messung geplant, damit ein einzelner verpasster oder
 * verzögerter Lauf die Kette nicht dauerhaft bricht.
 */
class MicLongTermAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MicLongTermCapture.runOneCycle(appContext)
            } catch (e: Exception) {
                Log.e("CodyLongTermMic", "runOneCycle fehlgeschlagen: ${e.message}", e)
            } finally {
                LongTermMicMonitor.scheduleNext(appContext)
                pendingResult.finish()
            }
        }
    }
}
