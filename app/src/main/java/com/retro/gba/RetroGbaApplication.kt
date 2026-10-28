package com.retro.gba

import android.app.Application
import com.retro.gba.util.AppLogger

class RetroGbaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
    }
}
