package com.example.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.Alarm
import com.example.data.model.QrCode
import com.example.data.model.WakeRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    @Update
    suspend fun updateAlarm(alarm: Alarm)

    @Delete
    suspend fun deleteAlarm(alarm: Alarm)
}

@Dao
interface QrCodeDao {
    @Query("SELECT * FROM qr_codes ORDER BY createdAt DESC")
    fun getAllQrCodes(): Flow<List<QrCode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrCode(qrCode: QrCode)

    @Delete
    suspend fun deleteQrCode(qrCode: QrCode)
}

@Dao
interface WakeRecordDao {
    @Query("SELECT * FROM wake_records ORDER BY dateStr DESC")
    fun getAllWakeRecords(): Flow<List<WakeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWakeRecord(record: WakeRecord)
}

@Database(entities = [Alarm::class, QrCode::class, WakeRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun qrCodeDao(): QrCodeDao
    abstract fun wakeRecordDao(): WakeRecordDao
}
