package eu.akaiko.rplayer

import android.app.Application

class RadioPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.installDefaultHandler()
        CrashLog.append("APP", "Application started (pid=${android.os.Process.myPid()})")
    }
}
