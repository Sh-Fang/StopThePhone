package com.stopthephone.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.stopthephone.data.RecordDao
import com.stopthephone.data.SettingsStore
import com.stopthephone.data.UsageSession
import com.stopthephone.domain.UsageEventKind
import com.stopthephone.domain.UsageEventLite
import com.stopthephone.domain.UsagePairing
import com.stopthephone.system.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 把系统的使用情况事件增量同步成前台片段存库。
 */
class UsageSync(
    private val context: Context,
    private val recordDao: RecordDao,
    private val settingsStore: SettingsStore,
) {

    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        if (!Permissions.hasUsageAccess(context)) {
            return@withLock
        }
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // 第一次同步时往回取系统还保留着的事件
            val from = settingsStore.usageSyncedUntil.first() ?: (now - INITIAL_LOOKBACK_MILLIS)
            val result = UsagePairing.pair(readEvents(from, now), now)
            recordDao.insertUsageSessions(result.sessions.map { UsageSession(packageName = it.packageName, startAt = it.startAt, endAt = it.endAt) })
            settingsStore.setUsageSyncedUntil(result.nextFrom)
        }
    }

    private fun readEvents(from: Long, to: Long): List<UsageEventLite> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val events = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        val result = mutableListOf<UsageEventLite>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == context.packageName) {
                continue
            }
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventKind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventKind.ALL_STOPPED
                else -> null
            } ?: continue
            result.add(UsageEventLite(event.packageName, event.timeStamp, kind))
        }
        return result
    }

    companion object {
        private val INITIAL_LOOKBACK_MILLIS = TimeUnit.DAYS.toMillis(10)
    }
}
