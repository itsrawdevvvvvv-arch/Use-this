package dev.rawdev.gaffer.agent

import android.content.Context
import dev.rawdev.gaffer.data.MemoryStore
import dev.rawdev.gaffer.service.NotificationRelayService
import dev.rawdev.gaffer.service.ScreenAgentService
import dev.rawdev.gaffer.util.Actions
import dev.rawdev.gaffer.util.Html
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ToolRunner(
    private val ctx: Context,
    private val ask: suspend (tool: String, description: String) -> Boolean
) {

    suspend fun execute(name: String, args: JSONObject): ToolResult = try {
        dispatch(name, args)
    } catch (t: Throwable) {
        ToolResult(false, "${name} failed: ${t.message ?: t.javaClass.simpleName}")
    }

    private suspend fun dispatch(name: String, a: JSONObject): ToolResult {
        return when (name) {

        "read_screen" -> {
            val svc = ScreenAgentService.instance
                ?: return ToolResult(false, "Screen control is off. Ask the user to enable Gaffer under Settings > Accessibility.")
            ToolResult(true, svc.describeScreen())
        }

        "tap_text" -> {
            guard()?.let { return it }
            val svc = ScreenAgentService.instance ?: return noService()
            ToolResult(true, svc.tapText(a.optString("text")))
        }

        "tap_point" -> {
            guard()?.let { return it }
            val svc = ScreenAgentService.instance ?: return noService()
            ToolResult(true, svc.tapPoint(a.optDouble("x", 0.5), a.optDouble("y", 0.5)))
        }

        "swipe" -> {
            val svc = ScreenAgentService.instance ?: return noService()
            ToolResult(true, svc.swipe(a.optString("direction", "up"), a.optDouble("distance", 0.5)))
        }

        "type_text" -> {
            guard()?.let { return it }
            val svc = ScreenAgentService.instance ?: return noService()
            ToolResult(true, svc.typeText(a.optString("text")))
        }

        "press" -> {
            val svc = ScreenAgentService.instance ?: return noService()
            val button = a.optString("button", "back").lowercase()
            if (button == "enter") ToolResult(true, svc.pressEnter())
            else ToolResult(true, svc.press(button))
        }

        "open_app" -> {
            val query = a.optString("name").trim()
            if (query.isEmpty()) return ToolResult(false, "Give an app name.")
            if (Actions.openApp(ctx, query)) return ToolResult(true, "Opened $query.")
            val matches = Actions.findApps(ctx, query)
            if (matches.isEmpty()) return ToolResult(false, "No installed app matched \"$query\".")
            val (pkg, label) = matches.first()
            if (Actions.openApp(ctx, pkg)) ToolResult(true, "Opened $label ($pkg).")
            else ToolResult(false, "Found $label but could not open it.")
        }

        "list_apps" -> {
            val q = a.optString("query")
            val matches = Actions.findApps(ctx, q)
            if (matches.isEmpty()) ToolResult(true, "No apps matched.")
            else ToolResult(true, matches.take(60).joinToString("\n") { "${it.second}  (${it.first})" })
        }

        "read_notifications" -> {
            if (!NotificationRelayService.connected.value) {
                ToolResult(false, "Notification access is not granted. Ask the user to enable the Gaffer notification relay.")
            } else {
                val limit = a.optInt("limit", 15)
                val items = NotificationRelayService.recent(limit)
                ToolResult(true, if (items.isEmpty()) "No recent notifications." else items.joinToString("\n"))
            }
        }

        "remember" -> {
            val text = a.optString("text").trim()
            if (text.isEmpty()) return ToolResult(false, "Nothing to remember.")
            MemoryStore.remember(ctx, text)
            ToolResult(true, "Saved to memory.")
        }

        "recall" -> {
            val items = MemoryStore.recall(ctx, a.optString("query"), 20)
            ToolResult(true, if (items.isEmpty()) "No matching notes." else items.joinToString("\n"))
        }

        "send_email" -> {
            val to = a.optString("to")
            val subject = a.optString("subject")
            val body = a.optString("body")
            val desc = "Email to ${to.ifBlank { "(no recipient)" }}\nSubject: $subject\n\n$body"
            if (!ask("send_email", desc)) return ToolResult(false, "The user declined this email.")
            if (Actions.composeEmail(ctx, to, subject, body)) ToolResult(true, "Opened a pre-filled email draft. The user still has to send it.")
            else ToolResult(false, "No mail app handled that.")
        }

        "send_whatsapp" -> {
            val phone = a.optString("phone")
            val message = a.optString("message")
            val desc = "WhatsApp to ${phone.ifBlank { "(pick a chat)" }}\n\n$message"
            if (!ask("send_whatsapp", desc)) return ToolResult(false, "The user declined this message.")
            if (Actions.whatsapp(ctx, phone, message)) ToolResult(true, "Opened WhatsApp with the message pre-filled. The user still has to send it.")
            else ToolResult(false, "WhatsApp is not installed or refused the link.")
        }

        "share_text" -> {
            val text = a.optString("text")
            val pkg = a.optString("package_name").ifBlank { null }
            if (!ask("share_text", "Share this text${if (pkg != null) " to $pkg" else ""}:\n\n$text")) {
                return ToolResult(false, "The user declined the share.")
            }
            if (Actions.shareText(ctx, text, pkg)) ToolResult(true, "Opened the share sheet with the text.")
            else ToolResult(false, "The share sheet did not open.")
        }

        "add_calendar_event" -> {
            val title = a.optString("title")
            val notes = a.optString("notes")
            val start = parseTime(a.optString("start"))
            val minutes = a.optInt("minutes", 60)
            val desc = "Calendar: $title at ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(start)} for $minutes min"
            if (!ask("add_calendar_event", desc)) return ToolResult(false, "The user declined the event.")
            val end = start + minutes * 60_000L
            if (Actions.calendarEvent(ctx, title, notes, start, end)) ToolResult(true, "Opened a pre-filled calendar event.")
            else ToolResult(false, "No calendar app handled that.")
        }

        "web_fetch" -> {
            val url = a.optString("url").trim()
            if (url.isEmpty()) return ToolResult(false, "No URL given.")
            val fixed = if (url.startsWith("http")) url else "https://$url"
            val text = fetch(fixed)
            ToolResult(true, text)
        }

        "wait" -> {
            val seconds = a.optDouble("seconds", 2.0).coerceIn(0.5, 20.0)
            delay((seconds * 1000).toLong())
            ToolResult(true, "Waited ${"%.1f".format(seconds)}s.")
        }

        "ask_user" -> ToolResult(
            true,
            "Handed control back to the user with the question: " + a.optString("question"),
            terminal = true
        )

        "task_done" -> ToolResult(true, a.optString("summary", "Done."), terminal = true)

        else -> ToolResult(false, "Unknown tool: $name")
        }
    }

    private fun noService() = ToolResult(
        false,
        "Screen control is off. Ask the user to switch Gaffer on under Settings > Accessibility > Gaffer."
    )

    /** Refuses to touch money apps even if the model asks. */
    private fun guard(): ToolResult? {
        val pkg = ScreenAgentService.instance?.foregroundPackage()?.lowercase() ?: return null
        val hit = BLOCKED.firstOrNull { pkg.contains(it) }
        return if (hit == null) null else ToolResult(
            false,
            "Blocked: the foreground app ($pkg) looks like a payments or banking app. Gaffer never taps or types inside those."
        )
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Gaffer/1.0")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (raw.isBlank()) return "Empty response (HTTP $code)."
            val text = if (raw.contains("<html", true) || raw.contains("<body", true)) Html.toText(raw) else raw
            if (text.length > 6000) text.take(6000) + "\n... (truncated)" else text
        } catch (e: Exception) {
            "Fetch failed: ${e.message}"
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTime(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return System.currentTimeMillis() + 3_600_000L
        val formats = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.getDefault())
                sdf.isLenient = false
                return sdf.parse(trimmed)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        // plain hour like "18:30" means today
        try {
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, parts[0].trim().toInt())
                cal.set(Calendar.MINUTE, parts[1].trim().toInt())
                cal.set(Calendar.SECOND, 0)
                return cal.timeInMillis
            }
        } catch (_: Exception) {
        }
        return System.currentTimeMillis() + 3_600_000L
    }

    companion object {
        private val BLOCKED = listOf(
            "paisa", "phonepe", "paytm", "upi", "cred", "bank", "sbi", "hdfc", "icici",
            "axis", "kotak", "google.android.gms", "wallet", "money", "zerodha", "groww", "kuvera"
        )
    }
}
