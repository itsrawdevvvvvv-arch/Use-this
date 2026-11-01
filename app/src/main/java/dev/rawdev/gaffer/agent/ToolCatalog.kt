package dev.rawdev.gaffer.agent

import org.json.JSONArray
import org.json.JSONObject

/** The tool schemas handed to the model, in OpenAI function-calling shape. */
object ToolCatalog {

    fun toolsJson(): JSONArray {
        val arr = JSONArray()
        arr.put(tool("read_screen", "Read what is on the screen right now: the foreground app, every button, field and label, and each element's tap position as a 0-1 fraction. Always call this before acting, and again after acting to verify.", JSONObject(), emptyList()))
        arr.put(tool("tap_text", "Tap the on-screen element whose label matches this text. Preferred over tap_point because it survives layout changes.", props("text" to "string"), listOf("text")))
        arr.put(tool("tap_point", "Tap a precise point on the screen. x and y are fractions from 0 to 1 (0,0 is top-left). Use only when the element has no usable label.", props("x" to "number", "y" to "number"), listOf("x", "y")))
        arr.put(tool("swipe", "Scroll or swipe the screen. direction is up, down, left or right. up means the content moves up, i.e. you scroll further down a list.", props("direction" to "string", "distance" to "number"), listOf("direction")))
        arr.put(tool("type_text", "Replace the text in the currently focused field. Tap the field first.", props("text" to "string"), listOf("text")))
        arr.put(tool("press", "Press a system button: back, home, recents or notifications.", props("button" to "string"), listOf("button")))
        arr.put(tool("open_app", "Open an installed app by its visible name or package id.", props("name" to "string"), listOf("name")))
        arr.put(tool("list_apps", "List installed apps whose name contains this text. Empty text lists all of them.", props("query" to "string"), emptyList()))
        arr.put(tool("read_notifications", "Read recent notifications, if the user granted the notification relay.", props("limit" to "integer"), emptyList()))
        arr.put(tool("remember", "Save a durable note for future runs: a client's preference, a login-free fact, a lead, a decision.", props("text" to "string"), listOf("text")))
        arr.put(tool("recall", "Search saved notes. Empty query returns the most recent ones.", props("query" to "string"), emptyList()))
        arr.put(tool("send_email", "Open the mail app with a pre-filled draft. The user still taps send. Requires approval.", props("to" to "string", "subject" to "string", "body" to "string"), listOf("subject", "body")))
        arr.put(tool("send_whatsapp", "Open a WhatsApp chat with a pre-filled message. Requires approval.", props("phone" to "string", "message" to "string"), listOf("message")))
        arr.put(tool("share_text", "Share text or a caption to another app such as Instagram, X or YouTube. Requires approval.", props("text" to "string", "package_name" to "string"), listOf("text")))
        arr.put(tool("add_calendar_event", "Open the calendar with a pre-filled event. start is an ISO-ish string like 2026-11-04 18:30. Requires approval.", props("title" to "string", "notes" to "string", "start" to "string", "minutes" to "integer"), listOf("title")))
        arr.put(tool("web_fetch", "Fetch a URL and return its readable text. Useful for research and lead finding.", props("url" to "string"), listOf("url")))
        arr.put(tool("wait", "Pause for a few seconds while a screen loads or a video plays.", props("seconds" to "number"), listOf("seconds")))
        arr.put(tool("ask_user", "Stop and hand control back to the human. Use for OTPs, passwords, payments, or any decision you must not make alone. This ends the run.", props("question" to "string"), listOf("question")))
        arr.put(tool("task_done", "Finish the run with a short report of what you actually did.", props("summary" to "string"), listOf("summary")))
        return arr
    }

    private fun props(vararg pairs: Pair<String, String>): JSONObject {
        val o = JSONObject()
        for ((name, type) in pairs) {
            o.put(name, JSONObject().put("type", type))
        }
        return o
    }

    private fun tool(name: String, description: String, properties: JSONObject, required: List<String>): JSONObject {
        val params = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("additionalProperties", false)
        if (required.isNotEmpty()) params.put("required", JSONArray(required))
        return JSONObject()
            .put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", description).put("parameters", params))
    }
}
