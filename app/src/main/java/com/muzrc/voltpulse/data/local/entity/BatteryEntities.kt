package com.muzrc.voltpulse.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_logs",
    indices = [Index(value = ["timestamp"])]
)
data class BatteryLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "level")
    val level: Int,
    @ColumnInfo(name = "current_ma")
    val currentMa: Float,
    @ColumnInfo(name = "voltage_mv")
    val voltageMv: Int,
    @ColumnInfo(name = "temperature_c")
    val temperatureC: Float,
    @ColumnInfo(name = "is_charging")
    val isCharging: Boolean,
    @ColumnInfo(name = "is_screen_on")
    val isScreenOn: Boolean
)

@Entity(tableName = "charge_sessions")
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0,
    @ColumnInfo(name = "session_type")
    val sessionType: String, // "CHARGE" atau "DISCHARGE"
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    @ColumnInfo(name = "start_level")
    val startLevel: Int,
    @ColumnInfo(name = "end_level")
    val endLevel: Int,
    @ColumnInfo(name = "mah_transferred")
    val mahTransferred: Float,
    @ColumnInfo(name = "max_temperature_c")
    val maxTemperatureC: Float,
    @ColumnInfo(name = "screen_on_duration_ms")
    val screenOnDurationMs: Long
)

@Entity(
    tableName = "app_usage_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ChargeSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["session_id"])]
)
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "app_label")
    val appLabel: String,
    @ColumnInfo(name = "foreground_time_ms")
    val foregroundTimeMs: Long,
    @ColumnInfo(name = "background_time_ms")
    val backgroundTimeMs: Long,
    @ColumnInfo(name = "estimated_mah")
    val estimatedMah: Float
)

@Entity(tableName = "battery_health_history")
data class BatteryHealthEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_timestamp")
    val dateTimestamp: Long,
    @ColumnInfo(name = "estimated_capacity_mah")
    val estimatedCapacityMah: Float,
    @ColumnInfo(name = "design_capacity_mah")
    val designCapacityMah: Float,
    @ColumnInfo(name = "internal_resistance_mohm")
    val internalResistanceMohm: Float? = null
)
