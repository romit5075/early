package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: String, // Comma separated: "Mon,Tue,Wed" etc or "" for once
    val isEnabled: Boolean = true,
    val isSnoozeEnabled: Boolean = false,
    val snoozeCount: Int = 0,
    val soundUri: String = "default_sound_1", // Default ringtone identifier
    val assignedQrCode: String = "" // QR/barcode content associated to turn off
)

@Entity(tableName = "qr_codes")
data class QrCode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // e.g. "Bathroom Mirror"
    val data: String, // barcode/QR string value
    val isGenerated: Boolean, // generated in app vs custom barcode scan
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "wake_records")
data class WakeRecord(
    @PrimaryKey val dateStr: String, // e.g. "yyyy-MM-dd"
    val wakeTime: String, // "07:30 AM"
    val status: String // "ON_TIME", "LATE", "MISSED"
)
