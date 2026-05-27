package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WakeApplication
import com.example.alarm.AlarmScheduler
import com.example.data.model.Alarm
import com.example.data.model.QrCode
import com.example.data.model.WakeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as WakeApplication).repository

    // Premium Subscription simulation
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    val alarms: StateFlow<List<Alarm>> = repository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val qrCodes: StateFlow<List<QrCode>> = repository.allQrCodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wakeRecords: StateFlow<List<WakeRecord>> = repository.allWakeRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Success state feedback for UI operations
    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    // Quotes pool
    val dailyQuote: String
        get() = motivationalQuotes[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % motivationalQuotes.size]

    init {
        // Pre-populate some wake history for beautiful default stats demo
        viewModelScope.launch {
            repository.allWakeRecords.collect { records ->
                if (records.isEmpty()) {
                    prepopulateStats()
                }
            }
        }
    }

    fun setPremium(premium: Boolean) {
        _isPremium.value = premium
    }

    fun clearStatus() {
        _operationStatus.value = null
    }

    // Alarm triggers
    fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = enabled)
            repository.updateAlarm(updated)
            if (enabled) {
                AlarmScheduler.schedule(getApplication(), updated)
            } else {
                AlarmScheduler.cancel(getApplication(), updated)
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication(), alarm)
            repository.deleteAlarm(alarm)
            _operationStatus.value = "Alarm deleted successfully"
        }
    }

    fun saveAlarm(
        id: Int = 0,
        hour: Int,
        minute: Int,
        label: String,
        days: Set<String>,
        isSnoozeEnabled: Boolean,
        soundUri: String,
        assignedQrCode: String,
        onLimitExceeded: () -> Unit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val currentAlarms = alarms.value
            // Free Tier enforcement: 2 alarms max
            if (id == 0 && !_isPremium.value && currentAlarms.size >= 2) {
                onLimitExceeded()
                return@launch
            }

            val daysStr = days.joinToString(",")
            val alarm = Alarm(
                id = id,
                hour = hour,
                minute = minute,
                label = label.ifEmpty { "Alarm" },
                days = daysStr,
                isEnabled = true,
                isSnoozeEnabled = isSnoozeEnabled,
                snoozeCount = 0,
                soundUri = soundUri,
                assignedQrCode = assignedQrCode
            )

            val newId = repository.insertAlarm(alarm)
            val savedAlarm = alarm.copy(id = if (id == 0) newId.toInt() else id)

            AlarmScheduler.schedule(getApplication(), savedAlarm)
            _operationStatus.value = "Alarm saved successfully"
            onSuccess()
        }
    }

    // QR Code actions
    fun registerQrCode(name: String, data: String, isGenerated: Boolean, onLimitExceeded: () -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val currentCodes = qrCodes.value
            // Free Tier enforcement: 1 QR maximum
            if (!_isPremium.value && currentCodes.size >= 1) {
                onLimitExceeded()
                return@launch
            }

            val qr = QrCode(
                name = name.ifEmpty { if (isGenerated) "Generated QR code" else "Registered product" },
                data = data,
                isGenerated = isGenerated
            )
            repository.insertQrCode(qr)
            _operationStatus.value = "QR Code configured successfully"
            onSuccess()
        }
    }

    fun deleteQrCode(qrCode: QrCode) {
        viewModelScope.launch {
            repository.deleteQrCode(qrCode)
        }
    }

    // Statistics compilation
    val streakFlow: StateFlow<Int> = repository.allWakeRecords.map { records ->
        var streak = 0
        val sorted = records.filter { it.status == "ON_TIME" }.sortedByDescending { it.dateStr }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        // Check consecutive days starting today or yesterday
        var currentCheckDate = sdf.format(cal.time)
        val hasWakeToday = sorted.any { it.dateStr == currentCheckDate }

        cal.add(Calendar.DAY_OF_YEAR, -1)
        var yesterdayStr = sdf.format(cal.time)

        if (hasWakeToday) {
            streak = 1
            var d = 1
            while (true) {
                val nextCal = Calendar.getInstance()
                nextCal.add(Calendar.DAY_OF_YEAR, -d)
                val checkStr = sdf.format(nextCal.time)
                if (sorted.any { it.dateStr == checkStr }) {
                    streak++
                    d++
                } else {
                    break
                }
            }
        } else if (sorted.any { it.dateStr == yesterdayStr }) {
            streak = 1
            var d = 2
            while (true) {
                val nextCal = Calendar.getInstance()
                nextCal.add(Calendar.DAY_OF_YEAR, -d)
                val checkStr = sdf.format(nextCal.time)
                if (sorted.any { it.dateStr == checkStr }) {
                    streak++
                    d++
                } else {
                    break
                }
            }
        }
        streak
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val earliestWakeTime: StateFlow<String> = repository.allWakeRecords.map { records ->
        val onTimes = records.filter { it.status == "ON_TIME" }
        if (onTimes.isEmpty()) return@map "--:--"

        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        var earliestCal: Calendar? = null

        for (rec in onTimes) {
            try {
                val date = sdf.parse(rec.wakeTime) ?: continue
                val cal = Calendar.getInstance().apply { time = date }
                if (earliestCal == null || cal.get(Calendar.HOUR_OF_DAY) < earliestCal.get(Calendar.HOUR_OF_DAY) ||
                    (cal.get(Calendar.HOUR_OF_DAY) == earliestCal.get(Calendar.HOUR_OF_DAY) &&
                            cal.get(Calendar.MINUTE) < earliestCal.get(Calendar.MINUTE))
                ) {
                    earliestCal = cal
                }
            } catch (e: Exception) {
                // Ignore parse failures
            }
        }
        earliestCal?.let { sdf.format(it.time) } ?: "06:15 AM"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "06:15 AM")

    val completedAlarmsMonth: StateFlow<Int> = repository.allWakeRecords.map { records ->
        records.filter { it.status == "ON_TIME" }.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private suspend fun prepopulateStats() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val statuses = listOf("ON_TIME", "ON_TIME", "ON_TIME", "LATE", "ON_TIME", "ON_TIME", "ON_TIME")
        val hours = listOf("06:30 AM", "06:45 AM", "07:12 AM", "08:15 AM", "06:50 AM", "07:05 AM", "06:15 AM")

        for (i in 1..7) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val dateStr = sdf.format(cal.time)
            repository.insertWakeRecord(
                WakeRecord(
                    dateStr = dateStr,
                    wakeTime = hours[i - 1],
                    status = statuses[i - 1]
                )
            )
        }
    }

    companion object {
        private val motivationalQuotes = listOf(
            "The body achieves what the mind believes. Scan the QR and show this morning who is boss! 🦁",
            "Snoozing is just practicing failure. Stand up, walk to your scan point, and conquer! ☀️",
            "An early morning walk is a blessing for the whole day. Scan is placed, let's go! 🌿",
            "Your future self will thank you for getting up right now. Scan that code! 🚀",
            "Action is the foundational key to all success. Scan to wake, live to dream!",
            "Rise up, start fresh, and see the bright opportunity in each new day!"
        )
    }
}
