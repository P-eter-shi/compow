package com.example.compow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertLogDao {

    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    suspend fun getAllAlertLogs(): List<AlertLogEntity>

    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    fun getAllAlertLogsFlow(): Flow<List<AlertLogEntity>>

    @Query("SELECT * FROM alert_logs WHERE is_resolved = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveAlert(): AlertLogEntity?

    @Query("SELECT * FROM alert_logs WHERE is_resolved = 0 ORDER BY timestamp DESC LIMIT 1")
    fun getActiveAlertFlow(): Flow<AlertLogEntity?>

    @Query("SELECT * FROM alert_logs WHERE logId = :logId")
    suspend fun getAlertLogById(logId: Long): AlertLogEntity?

    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAlerts(limit: Int = 10): List<AlertLogEntity>

    @Query("SELECT * FROM alert_logs WHERE alert_type = :alertType ORDER BY timestamp DESC")
    suspend fun getAlertLogsByType(alertType: AlertType): List<AlertLogEntity>

    @Query("SELECT * FROM alert_logs WHERE is_resolved = :isResolved ORDER BY timestamp DESC")
    suspend fun getAlertLogsByStatus(isResolved: Boolean): List<AlertLogEntity>

    @Query("SELECT * FROM alert_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getAlertLogsByDateRange(startTime: Long, endTime: Long): List<AlertLogEntity>

    @Insert
    suspend fun insertAlertLog(alertLog: AlertLogEntity): Long

    @Insert
    suspend fun insertAlertLogs(alertLogs: List<AlertLogEntity>): List<Long>

    @Update
    suspend fun updateAlertLog(alertLog: AlertLogEntity)

    @Query("UPDATE alert_logs SET is_resolved = 1, resolved_at = :resolvedAt WHERE logId = :logId")
    suspend fun resolveAlert(logId: Long, resolvedAt: Long = System.currentTimeMillis())

    @Query("UPDATE alert_logs SET is_resolved = 1, resolved_at = :resolvedAt WHERE is_resolved = 0")
    suspend fun resolveAllActiveAlerts(resolvedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteAlertLog(alertLog: AlertLogEntity)

    @Query("DELETE FROM alert_logs WHERE logId = :logId")
    suspend fun deleteAlertLogById(logId: Long)

    @Query("DELETE FROM alert_logs WHERE timestamp < :timestamp")
    suspend fun deleteOldAlerts(timestamp: Long)

    @Query("DELETE FROM alert_logs")
    suspend fun deleteAllAlertLogs()

    @Query("SELECT COUNT(*) FROM alert_logs")
    suspend fun getAlertLogCount(): Int

    @Query("SELECT COUNT(*) FROM alert_logs WHERE is_resolved = 0")
    suspend fun getActiveAlertCount(): Int

    @Query("SELECT COUNT(*) FROM alert_logs WHERE alert_type = :alertType")
    suspend fun getAlertCountByType(alertType: AlertType): Int

    @Query("SELECT SUM(contacts_notified) FROM alert_logs")
    suspend fun getTotalContactsNotified(): Int?

    @Query("DELETE FROM alert_logs")
    suspend fun deleteAllAlerts()
}