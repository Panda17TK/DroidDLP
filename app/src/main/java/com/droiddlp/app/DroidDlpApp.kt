package com.droiddlp.app

import android.app.Application
import com.droiddlp.app.settings.SettingsStore

/** Application entry point; initialises [SettingsStore] before any screen/service. */
class DroidDlpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
    }
}
