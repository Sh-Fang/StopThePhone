package com.stopthephone

import android.app.Application
import com.stopthephone.data.AppDatabase
import com.stopthephone.data.ControlRepository
import com.stopthephone.data.InstalledApps
import com.stopthephone.data.SettingsStore

class StopThePhoneApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val controlRepository: ControlRepository by lazy { ControlRepository(database.controlDao(), database.recordDao()) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val installedApps: InstalledApps by lazy { InstalledApps(this) }
}
