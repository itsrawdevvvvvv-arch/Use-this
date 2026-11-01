package dev.rawdev.gaffer

import android.app.Application
import dev.rawdev.gaffer.agent.AgentEngine
import dev.rawdev.gaffer.data.Prefs

class GafferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AgentEngine.init(this)
        Prefs.of(this)
    }
}
