package com.stopthephone.data

import com.stopthephone.domain.PeriodRule
import kotlinx.coroutines.flow.Flow

class ControlRepository(private val dao: ControlDao) {

    val apps: Flow<List<ControlledApp>> = dao.observeApps()

    val periods: Flow<List<ControlPeriod>> = dao.observePeriods()

    suspend fun applySelection(added: List<InstalledApp>, removed: List<String>, now: Long) {
        dao.applySelection(added.map { ControlledApp(it.packageName, it.label, now) }, removed)
    }

    suspend fun savePeriod(period: ControlPeriod) {
        if (period.id == 0L) {
            dao.insertPeriod(period)
        } else {
            dao.updatePeriod(period)
        }
    }

    suspend fun deletePeriod(id: Long) = dao.deletePeriod(id)

    suspend fun copyPeriods(from: String, targets: List<String>) = dao.copyPeriods(from, targets)
}

fun ControlPeriod.toRule(): PeriodRule = PeriodRule(startMinute, endMinute, daysMask)
