package com.example.data.repository

import com.example.data.database.AlarmDao
import com.example.data.database.QrCodeDao
import com.example.data.database.WakeRecordDao
import com.example.data.model.Alarm
import com.example.data.model.QrCode
import com.example.data.model.WakeRecord
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val qrCodeDao: QrCodeDao,
    private val wakeRecordDao: WakeRecordDao
) {
    val allAlarms: Flow<List<Alarm>> = alarmDao.getAllAlarms()
    val allQrCodes: Flow<List<QrCode>> = qrCodeDao.getAllQrCodes()
    val allWakeRecords: Flow<List<WakeRecord>> = wakeRecordDao.getAllWakeRecords()

    suspend fun getAlarmById(id: Int): Alarm? = alarmDao.getAlarmById(id)

    suspend fun insertAlarm(alarm: Alarm): Long = alarmDao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: Alarm) = alarmDao.updateAlarm(alarm)

    suspend fun deleteAlarm(alarm: Alarm) = alarmDao.deleteAlarm(alarm)

    suspend fun insertQrCode(qrCode: QrCode) = qrCodeDao.insertQrCode(qrCode)

    suspend fun deleteQrCode(qrCode: QrCode) = qrCodeDao.deleteQrCode(qrCode)

    suspend fun insertWakeRecord(record: WakeRecord) = wakeRecordDao.insertWakeRecord(record)
}
