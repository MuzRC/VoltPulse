package com.muzrc.voltpulse.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.muzrc.voltpulse.data.local.dao.BatteryDao
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity
import com.muzrc.voltpulse.data.local.entity.BatteryHealthEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity

@Database(
    entities = [
        BatteryLogEntity::class,
        ChargeSessionEntity::class,
        AppUsageEntity::class,
        BatteryHealthEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoltPulseDatabase : RoomDatabase() {

    abstract fun batteryDao(): BatteryDao

    companion object {
        @Volatile
        private var INSTANCE: VoltPulseDatabase? = null

        fun getInstance(context: Context): VoltPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoltPulseDatabase::class.java,
                    "voltpulse_telemetry.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
