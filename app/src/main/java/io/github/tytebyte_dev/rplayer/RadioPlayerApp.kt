package io.github.tytebyte_dev.rplayer

import android.app.Application

class RadioPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.init(this)
        CrashLog.installDefaultHandler()
    }
}
