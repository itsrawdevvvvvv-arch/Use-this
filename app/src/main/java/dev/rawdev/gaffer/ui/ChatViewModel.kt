package dev.rawdev.gaffer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.rawdev.gaffer.agent.AgentEngine
import dev.rawdev.gaffer.data.MemoryStore
import dev.rawdev.gaffer.data.Prefs
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val prefs: Prefs = Prefs.of(app)
    val state: StateFlow<dev.rawdev.gaffer.agent.AgentState> = AgentEngine.state

    fun send(text: String) = AgentEngine.send(text)

    fun stop() = AgentEngine.stop()

    fun reset() = AgentEngine.reset()

    fun approve(id: String, allowed: Boolean) = AgentEngine.respondApproval(id, allowed)

    fun memory(): List<String> = MemoryStore.all(getApplication())

    fun clearMemory() = MemoryStore.clear(getApplication())
}
