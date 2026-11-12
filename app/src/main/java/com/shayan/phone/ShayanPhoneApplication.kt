package com.shayan.phone

import android.app.Application

class ShayanPhoneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneErrorLog.install(applicationContext)
        PhoneErrorLog.add(this, "PROCESS_START", "Application process started")
    }
}
