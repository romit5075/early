package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.Alarm
import java.util.Calendar

object AlarmScheduler {
    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Exact alarms permission is not granted!")
            }
        }

        val triggerTime = calculateNextTriggerTime(alarm)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_ALARM"
            putExtra("ALARM_ID", alarm.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val showIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_SHOW_ALARM_CLOCK"
            putExtra("ALARM_ID", alarm.id)
        }
        val showPendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id + 100000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d("AlarmScheduler", "Scheduled alarm ${alarm.id} for timeInMillis: $triggerTime")
    }

    fun cancel(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.ACTION_TRIGGER_ALARM"
            putExtra("ALARM_ID", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        Log.d("AlarmScheduler", "Cancelled alarm ${alarm.id}")
    }

    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = Calendar.getInstance()

        if (alarm.days.isEmpty()) {
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }

        val dayList = alarm.days.split(",").map { it.trim() }.toSet()
        val calendarDays = mapOf(
            "Sun" to Calendar.SUNDAY,
            "Mon" to Calendar.MONDAY,
            "Tue" to Calendar.TUESDAY,
            "Wed" to Calendar.WEDNESDAY,
            "Thu" to Calendar.THURSDAY,
            "Fri" to Calendar.FRIDAY,
            "Sat" to Calendar.SATURDAY
        )

        var closestTriggerTime = Long.MAX_VALUE
        for (dayName in dayList) {
            val targetDay = calendarDays[dayName] ?: continue
            val tempCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val currentDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            var daysUntilTarget = targetDay - currentDayOfWeek
            if (daysUntilTarget < 0 || (daysUntilTarget == 0 && tempCal.before(now))) {
                daysUntilTarget += 7
            }
            tempCal.add(Calendar.DAY_OF_YEAR, daysUntilTarget)

            if (tempCal.timeInMillis < closestTriggerTime) {
                closestTriggerTime = tempCal.timeInMillis
            }
        }

        return if (closestTriggerTime == Long.MAX_VALUE) {
            if (calendar.before(now)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            calendar.timeInMillis
        } else {
            closestTriggerTime
        }
    }
}
