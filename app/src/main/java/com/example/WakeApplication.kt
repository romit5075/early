package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.repository.AlarmRepository

class WakeApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var repository: AlarmRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "qr_wake_database"
        ).fallbackToDestructiveMigration().build()

        repository = AlarmRepository(
            database.alarmDao(),
            database.qrCodeDao(),
            database.wakeRecordDao()
        )
    }

    companion object {
        lateinit var instance: WakeApplication
            private set
    }
}
