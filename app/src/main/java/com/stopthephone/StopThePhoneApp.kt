package com.stopthephone

import android.app.Application
import com.stopthephone.data.AppDatabase

class StopThePhoneApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
}
