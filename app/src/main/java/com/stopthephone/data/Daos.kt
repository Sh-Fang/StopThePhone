package com.stopthephone.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlDao {

    @Query("SELECT * FROM controlled_app ORDER BY createdAt")
    fun observeApps(): Flow<List<ControlledApp>>

    @Query("SELECT * FROM control_period ORDER BY startMinute, id")
    fun observePeriods(): Flow<List<ControlPeriod>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApps(apps: List<ControlledApp>)

    @Query("DELETE FROM controlled_app WHERE packageName IN (:packageNames)")
    suspend fun deleteApps(packageNames: List<String>)

    @Query("DELETE FROM control_period WHERE packageName IN (:packageNames)")
    suspend fun deletePeriodsOf(packageNames: List<String>)

    @Insert
    suspend fun insertPeriod(period: ControlPeriod): Long

    @Insert
    suspend fun insertPeriods(periods: List<ControlPeriod>)

    @Update
    suspend fun updatePeriod(period: ControlPeriod)

    @Query("DELETE FROM control_period WHERE id = :id")
    suspend fun deletePeriod(id: Long)

    @Query("SELECT * FROM control_period WHERE packageName = :packageName")
    suspend fun periodsOf(packageName: String): List<ControlPeriod>

    /**
     * 新增和移除管控应用，移除时连同它的时段一起删。
     */
    @Transaction
    suspend fun applySelection(added: List<ControlledApp>, removed: List<String>) {
        insertApps(added)
        if (removed.isNotEmpty()) {
            deletePeriodsOf(removed)
            deleteApps(removed)
        }
    }

    /**
     * 把源应用的全部时段复制到目标应用，覆盖目标应用原有时段。
     */
    @Transaction
    suspend fun copyPeriods(from: String, targets: List<String>) {
        val source = periodsOf(from)
        deletePeriodsOf(targets)
        insertPeriods(targets.flatMap { target -> source.map { it.copy(id = 0, packageName = target) } })
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

    /**
     * 把进程被杀时没来得及收尾的拦截事件记为放弃。
     */
    @Query("UPDATE intercept_event SET closedAt = :closedAt, outcome = 'GAVE_UP' WHERE outcome IS NULL")
    suspend fun closeOpenIntercepts(closedAt: Long)

    @Query("SELECT * FROM `release` WHERE purpose = 'RELEASE' AND endAt > :now")
    suspend fun activeReleases(now: Long): List<Release>

    @Insert
    suspend fun insertUsageSessions(sessions: List<UsageSession>)
}
