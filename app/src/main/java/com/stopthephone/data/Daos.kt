package com.stopthephone.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlDao {

    @Query("SELECT * FROM controlled_app ORDER BY createdAt")
    fun observeApps(): Flow<List<ControlledApp>>

    @Query("SELECT * FROM control_period")
    fun observePeriods(): Flow<List<ControlPeriod>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: ControlledApp)

    @Query("DELETE FROM controlled_app WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)

    @Query("DELETE FROM control_period WHERE packageName = :packageName")
    suspend fun deletePeriods(packageName: String)

    @Insert
    suspend fun insertPeriods(periods: List<ControlPeriod>)

    /**
     * 用新时段整体覆盖某个应用的原有时段。
     */
    @Transaction
    suspend fun replacePeriods(packageName: String, periods: List<ControlPeriod>) {
        deletePeriods(packageName)
        insertPeriods(periods)
    }
}

@Dao
interface RecordDao {

    @Insert
    suspend fun insertRelease(release: Release): Long

    @Insert
    suspend fun insertIntercept(event: InterceptEvent): Long

    @Query("UPDATE intercept_event SET closedAt = :closedAt, outcome = :outcome, releaseId = :releaseId WHERE id = :id")
    suspend fun closeIntercept(id: Long, closedAt: Long, outcome: InterceptOutcome, releaseId: Long?)

    @Query("SELECT * FROM `release` WHERE packageName = :packageName AND purpose = 'RELEASE' AND endAt > :now ORDER BY endAt DESC LIMIT 1")
    suspend fun activeRelease(packageName: String, now: Long): Release?

    @Insert
    suspend fun insertUsageSessions(sessions: List<UsageSession>)
}
