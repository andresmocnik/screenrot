package com.screenrot.app

import android.app.Application
import com.screenrot.app.work.CharacterUpdateWorker

class ScreenRotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Idempotent: WorkManager's KEEP policy means this is a no-op if already scheduled.
        CharacterUpdateWorker.schedule(this)
    }
}
