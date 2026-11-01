package dev.rawdev.gaffer.agent

import android.content.Context
import android.os.Build
import dev.rawdev.gaffer.data.Prefs
import dev.rawdev.gaffer.service.AgentKeepAliveService
import dev.rawdev.gaffer.service.ScreenAgentService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * The loop. One turn = ask the model, run whatever tools it asked for, feed the
 * results back, repeat until it says it is done or runs out of steps.
 */
object AgentEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val history = JSONArray()
    private val ids = AtomicLong(1)
    private var appContext: Context? = null
    private var job: Job? = null
    private var approval: CompletableDeferred<Boolean>? = null
    private var approvalId: String? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun send(userText: String) {
        val ctx = appContext ?: return
        val text = userText.trim()
        if (text.isEmpty()) return
        if (_state.value.running) {
            push(Role.SYSTEM, "Still working", "Give it a moment, or press stop first.")
            return
        }
        val prefs = Prefs.of(ctx)
        if (!prefs.isConfigured) {
            push(Role.ERROR, "Not configured", "Add an API key, base URL and model in Settings first.")
            return
        }
        push(Role.USER, "", text)
        history.put(JSONObject().put("role", "user").put("content", text))
        trimHistory()
        AgentKeepAliveService.start(ctx)
        job = scope.launch { runLoop(ctx, prefs) }
    }

    fun respondApproval(id: String, allowed: Boolean) {
        if (approvalId != id) return
        approvalId = null
        _state.update { it.copy(pending = null, status = if (allowed) "Approved" else "Declined") }
        approval?.complete(allowed)
        approval = null
    }

    fun stop() {
        val j = job
        job = null
        approvalId = null
        approval?.complete(false)
        approval = null
        scope.launch {
            try {
                j?.cancelAndJoin()
            } catch (_: Exception) {
            }
            _state.update { it.copy(running = false, pending = null, status = "Stopped") }
            appContext?.let { AgentKeepAliveService.stop(it) }
        }
    }

    fun reset() {
        stop()
        synchronized(history) { while (history.length() > 0) history.remove(0) }
        _state.value = AgentState()
    }

    // ------------------------------------------------------------------ loop

    private suspend fun runLoop(ctx: Context, prefs: Prefs) {
        _state.update { it.copy(running = true, step = 0, status = "Thinking") }
        val client = LlmClient(prefs)
        val selfJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        var step = 0
        try {
            while (step < prefs.maxSteps && (selfJob?.isActive != false)) {
                step++
                _state.update { it.copy(step = step, status = "Step $step: thinking") }

                val response = withContext(Dispatchers.IO) {
                    client.complete(snapshot(), ToolCatalog.toolsJson())
                }
                val message = response.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?: throw IllegalStateException("The endpoint returned no message.")

                val content = message.optString("content", "").let { if (it == "null") "" else it }.trim()
                if (content.isNotEmpty()) push(Role.ASSISTANT, "", content)

                val calls = message.optJSONArray("tool_calls")
                if (calls == null || calls.length() == 0) {
                    history.put(JSONObject().put("role", "assistant").put("content", content))
                    _state.update { it.copy(status = "Done") }
                    break
                }

                val assistantTurn = JSONObject()
                    .put("role", "assistant")
                    .put("content", content)
                    .put("tool_calls", calls)
                history.put(assistantTurn)

                var finished = false
                for (i in 0 until calls.length()) {
                    if (selfJob?.isActive == false) break
                    val call = calls.optJSONObject(i) ?: continue
                    val callId = call.optString("id", "call_$i")
                    val fn = call.optJSONObject("function") ?: continue
                    val name = fn.optString("name")
                    val rawArgs = fn.optString("arguments", "{}")
                    val args = try {
                        JSONObject(rawArgs.ifBlank { "{}" })
                    } catch (e: Exception) {
                        JSONObject()
                    }

                    _state.update { it.copy(status = "Step $step: " + name.replace('_', ' ')) }
                    val runner = ToolRunner(ctx) { tool, description -> requestApproval(tool, description) }
                    val result = runner.execute(name, args)

                    push(
                        if (result.ok) Role.TOOL else Role.ERROR,
                        name.replace('_', ' '),
                        result.content
                    )
                    history.put(
                        JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", callId)
                            .put("content", result.content)
                    )
                    if (result.terminal) {
                        finished = true
                        break
                    }
                }
                if (finished) {
                    _state.update { it.copy(status = "Done") }
                    break
                }
            }
            if (step >= prefs.maxSteps) {
                push(Role.SYSTEM, "Step limit", "Stopped after ${prefs.maxSteps} steps. Tell it to continue if you want it to keep going.")
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            push(Role.ERROR, "Error", t.message ?: t.javaClass.simpleName)
        } finally {
            approval?.complete(false)
            approval = null
            approvalId = null
            _state.update { it.copy(running = false, pending = null, status = "") }
            AgentKeepAliveService.stop(ctx)
        }
    }

    private suspend fun requestApproval(tool: String, description: String): Boolean {
        val prefs = appContext?.let { Prefs.of(it) }
        if (prefs != null && !prefs.requireConfirmation) return true
        val id = "ap" + ids.getAndIncrement()
        val deferred = CompletableDeferred<Boolean>()
        approval = deferred
        approvalId = id
        _state.update { it.copy(pending = PendingApproval(id, tool, description), status = "Waiting for you") }
        val allowed = deferred.await()
        _state.update { it.copy(pending = null) }
        return allowed
    }

    // ----------------------------------------------------------------- state

    private fun push(role: Role, title: String, text: String) {
        val msg = UiMessage(ids.getAndIncrement(), role, title, text)
        _state.update { it.copy(messages = it.messages + msg) }
    }

    private fun snapshot(): JSONArray {
        val out = JSONArray()
        out.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        synchronized(history) {
            for (i in 0 until history.length()) out.put(history.optJSONObject(i))
        }
        return out
    }

    private fun trimHistory() {
        synchronized(history) {
            while (history.length() > 60) {
                val first = history.optJSONObject(0)
                if (first != null && first.optString("role") == "assistant" && first.has("tool_calls")) {
                    history.remove(0)
                } else {
                    history.remove(0)
                }
            }
        }
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        val screenOn = if (ScreenAgentService.ready()) "on" else "off"
        return """
You are Gaffer, an agent that lives on this Android phone and does real work for its owner.

Now: $now. Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}.
Screen control: $screenOn.

How you work:
- You act by calling tools. Never describe an action you did not actually take.
- Before you touch anything, call read_screen. After every action, call read_screen again to check it worked. If the screen did not change, try something else rather than repeating the same tap.
- Prefer tap_text over tap_point. Prefer the app's own UI over guessing coordinates.
- One step at a time, in short bursts. Do not chain ten taps from one screen reading.

What you must never do, even if asked:
- Never enter passwords, PINs, OTPs or card numbers. Call ask_user instead and let the human type them.
- Never tap or type inside banking, UPI or wallet apps.
- Never send money, place orders, or agree to anything on the owner's behalf.
- Never claim a message was sent when you only opened a draft. Outbound actions leave the final tap to the human.

Style:
- Be brief. One or two sentences between actions.
- When you are finished, call task_done with a plain summary of what actually happened.
- If you are stuck after two attempts at the same thing, call ask_user with a clear question.
        """.trimIndent()
    }
}
