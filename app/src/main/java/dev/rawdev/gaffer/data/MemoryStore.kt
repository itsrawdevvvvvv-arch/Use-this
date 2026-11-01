package dev.rawdev.gaffer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tiny persistent scratchpad the agent can write to and read back later. */
object MemoryStore {

    private fun file(ctx: Context) = File(ctx.filesDir, "gaffer_memory.json")

    private fun load(ctx: Context): JSONArray {
        val f = file(ctx)
        if (!f.exists()) return JSONArray()
        return try {
            JSONArray(f.readText())
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun save(ctx: Context, arr: JSONArray) {
        try {
            file(ctx).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun remember(ctx: Context, text: String) {
        val arr = load(ctx)
        arr.put(
            JSONObject().apply {
                put("text", text.trim())
                put("at", System.currentTimeMillis())
            }
        )
        while (arr.length() > 400) arr.remove(0)
        save(ctx, arr)
    }

    @Synchronized
    fun recall(ctx: Context, query: String, limit: Int = 15): List<String> {
        val arr = load(ctx)
        val q = query.trim().lowercase()
        val out = ArrayList<String>()
        for (i in arr.length() - 1 downTo 0) {
            val t = arr.optJSONObject(i)?.optString("text").orEmpty()
            if (t.isBlank()) continue
            if (q.isEmpty() || t.lowercase().contains(q)) out.add(t)
            if (out.size >= limit) break
        }
        return out
    }

    @Synchronized
    fun all(ctx: Context): List<String> {
        val arr = load(ctx)
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            out.add(arr.optJSONObject(i)?.optString("text").orEmpty())
        }
        return out.filter { it.isNotBlank() }
    }

    @Synchronized
    fun clear(ctx: Context) {
        save(ctx, JSONArray())
    }
}
