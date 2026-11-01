package dev.rawdev.gaffer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.net.URLEncoder

/**
 * Outbound actions that go through the normal Android intents, so the user
 * still sees and confirms them in the app that owns them.
 */
object Actions {

    fun launch(ctx: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    fun openUrl(ctx: Context, url: String): Boolean {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        return launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
    }

    fun openApp(ctx: Context, packageName: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(ctx, i)
    }

    /** Returns package names whose visible label matches the query. */
    fun findApps(ctx: Context, query: String): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val q = query.trim().lowercase()
        val out = ArrayList<Pair<String, String>>()
        for (ri in resolved) {
            val label = ri.loadLabel(pm).toString()
            val pkg = ri.activityInfo.packageName
            if (out.any { it.first == pkg }) continue
            if (q.isEmpty() || label.lowercase().contains(q)) out.add(pkg to label)
        }
        return out.sortedBy { it.second }
    }

    fun composeEmail(ctx: Context, to: String, subject: String, body: String): Boolean {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to)))
        i.putExtra(Intent.EXTRA_SUBJECT, subject)
        i.putExtra(Intent.EXTRA_TEXT, body)
        return launch(ctx, i)
    }

    fun whatsapp(ctx: Context, phone: String, message: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        val text = URLEncoder.encode(message, "UTF-8")
        val url = if (digits.isBlank()) "https://wa.me/?text=$text" else "https://wa.me/$digits?text=$text"
        return launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun shareText(ctx: Context, text: String, packageName: String?): Boolean {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        if (!packageName.isNullOrBlank()) i.setPackage(packageName)
        val chooser = Intent.createChooser(i, "Share with")
        return launch(ctx, chooser)
    }

    fun calendarEvent(ctx: Context, title: String, notes: String, startMillis: Long, endMillis: Long): Boolean {
        val i = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, notes)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
        return launch(ctx, i)
    }
}
