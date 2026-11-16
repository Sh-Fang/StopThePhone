package com.stopthephone

import android.app.Application
import com.stopthephone.data.AppDatabase
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.InstalledApps
import com.stopthephone.data.SettingsStore
import com.stopthephone.data.StatsRepository
import com.stopthephone.usage.UsageSync
import com.stopthephone.usage.UsageSyncWorker

class StopThePhoneApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val controlRepository: ControlRepository by lazy { ControlRepository(database.controlDao(), database.recordDao()) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val installedApps: InstalledApps by lazy { InstalledApps(this) }
    val statsRepository: StatsRepository by lazy { StatsRepository(database.controlDao(), database.recordDao()) }
    val usageSync: UsageSync by lazy { UsageSync(this, database.recordDao(), settingsStore) }

    override fun onCreate() {
        super.onCreate()
        UsageSyncWorker.schedule(this)
    }
}
