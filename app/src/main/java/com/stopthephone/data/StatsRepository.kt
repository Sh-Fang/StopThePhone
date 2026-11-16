package com.stopthephone.data

import com.stopthephone.domain.InterceptLite
import com.stopthephone.domain.ReleaseLite
import com.stopthephone.domain.SessionLite
import com.stopthephone.domain.StatsInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class StatsRepository(private val controlDao: ControlDao, private val recordDao: RecordDao) {

    /**
     * 读取统计范围内的记录，管控时段取当前配置。
     */
    suspend fun input(from: Long, to: Long): StatsInput {
        val periods = controlDao.observePeriods().first().groupBy { it.packageName }
        val apps = controlDao.observeApps().first()
        return StatsInput(
            periods = apps.associate { app -> app.packageName to periods[app.packageName].orEmpty().map { it.toRule() } },
            sessions = recordDao.sessionsBetween(from, to).map { SessionLite(it.packageName, it.startAt, it.endAt) },
            intercepts = recordDao.interceptsBetween(from, to).map {
                InterceptLite(it.packageName, it.shownAt, it.closedAt, it.outcome?.let { outcome -> outcome == InterceptOutcome.RELEASED })
            },
            releases = recordDao.releasesBetween(from, to).mapNotNull { release ->
                release.endAt?.let { ReleaseLite(release.packageName, release.createdAt, it) }
            },
        )
    }

    fun reasons(packageName: String?, from: Long, to: Long): Flow<List<Release>> = recordDao.observeReasons(packageName, from, to)

    val reasonApps: Flow<List<String>> = recordDao.observeReasonApps()

    val apps: Flow<List<ControlledApp>> = controlDao.observeApps()
}
