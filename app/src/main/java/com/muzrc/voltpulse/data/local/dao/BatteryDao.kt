package com.muzrc.voltpulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muzrc.voltpulse.data.local.entity.AppUsageEntity
import com.muzrc.voltpulse.data.local.entity.BatteryHealthEntity
import com.muzrc.voltpulse.data.local.entity.BatteryLogEntity
import com.muzrc.voltpulse.data.local.entity.ChargeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    // --- Battery Logs ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogsBatch(logs: List<BatteryLogEntity>)

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLogsSince(sinceTimestamp: Long): Flow<List<BatteryLogEntity>>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getLogsSinceSync(sinceTimestamp: Long): List<BatteryLogEntity>

    @Query("SELECT AVG(ABS(current_ma)) FROM battery_logs WHERE is_charging = 0 AND is_screen_on = 1 AND timestamp >= :sinceTimestamp")
    suspend fun getAverageActiveDischargeMa(sinceTimestamp: Long): Float?

    @Query("SELECT AVG(ABS(current_ma)) FROM battery_logs WHERE is_charging = 0 AND is_screen_on = 0 AND timestamp >= :sinceTimestamp")
    suspend fun getAverageStandbyDischargeMa(sinceTimestamp: Long): Float?

    @Query("DELETE FROM battery_logs WHERE timestamp < :olderThanTimestamp")
    suspend fun deleteLogsOlderThan(olderThanTimestamp: Long): Int

    // --- Charge / Discharge Sessions ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargeSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargeSessionEntity)

    @Query("SELECT * FROM charge_sessions ORDER BY start_time DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 50): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY start_time DESC LIMIT 1")
    suspend fun getLatestSession(): ChargeSessionEntity?

    // --- App Usage Snapshots ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsageBatch(usages: List<AppUsageEntity>)

    @Query("SELECT * FROM app_usage_snapshots WHERE session_id = :sessionId ORDER BY estimated_mah DESC")
    fun getAppUsageForSession(sessionId: Long): Flow<List<AppUsageEntity>>

    // --- Battery Health History ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthRecord(record: BatteryHealthEntity)

    @Query("SELECT * FROM battery_health_history ORDER BY date_timestamp DESC LIMIT 10")
    fun getRecentHealthRecords(): Flow<List<BatteryHealthEntity>>

    @Query("SELECT * FROM battery_health_history ORDER BY date_timestamp DESC LIMIT 10")
    suspend fun getRecentHealthRecordsSync(): List<BatteryHealthEntity>
}
