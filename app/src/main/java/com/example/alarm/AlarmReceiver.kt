package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.WakeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("AlarmReceiver", "Received Broadcast: $action")

        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1

        when (action) {
            "com.example.ACTION_TRIGGER_ALARM" -> {
                if (alarmId != -1) {
                    triggerAlarm(context, alarmId)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                restoreAlarms(context)
            }
        }
    }

    private fun triggerAlarm(context: Context, alarmId: Int) {
        val app = context.applicationContext as WakeApplication
        val repo = app.repository

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = repo.getAlarmById(alarmId)
            if (alarm != null && alarm.isEnabled) {
                Log.d("AlarmReceiver", "Alarm ${alarm.id} is enabled, starting service and activity")

                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    putExtra("ALARM_ID", alarm.id)
                    putExtra("SOUND_URI", alarm.soundUri)
                }
                context.startForegroundService(serviceIntent)

                // Open fullscreen activity
                val ringingIntent = Intent().apply {
                    setClassName(context.packageName, "com.example.alarm.AlarmRingingActivity")
                    putExtra("ALARM_ID", alarm.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(ringingIntent)

                if (alarm.days.isEmpty()) {
                    repo.updateAlarm(alarm.copy(isEnabled = false))
                } else {
                    AlarmScheduler.schedule(context, alarm)
                }
            }
        }
    }

    private fun restoreAlarms(context: Context) {
        val app = context.applicationContext as WakeApplication
        val repo = app.repository

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = repo.allAlarms.first()
                for (alarm in alarms) {
                    if (alarm.isEnabled) {
                        AlarmScheduler.schedule(context, alarm)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error restoring active alarms on boot", e)
            }
        }
    }
}
