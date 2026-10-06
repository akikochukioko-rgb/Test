package eu.akaiko.rplayer

import android.app.Application

class RadioPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            CrashLog.init(this)
            CrashLog.installDefaultHandler()
            CrashLog.append("APP", "Application started (pid=${android.os.Process.myPid()})")
        } catch (_: Throwable) {
            // Never let logging prevent app start
        }
    }
}
