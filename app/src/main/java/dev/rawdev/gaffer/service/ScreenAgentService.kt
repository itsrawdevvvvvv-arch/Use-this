package dev.rawdev.gaffer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The hands and eyes of the agent. Reads the accessibility tree and injects
 * taps, swipes and text. Only active when the user has switched it on in
 * Settings > Accessibility.
 */
class ScreenAgentService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        connected.value = false
        super.onDestroy()
    }

    // ---------------------------------------------------------------- screen

    fun describeScreen(): String {
        val root = rootInActiveWindow
            ?: return "No readable window. The screen may be off, locked, or showing a secure/private view."
        val sb = StringBuilder()
        val pkg = root.packageName?.toString().orEmpty()
        sb.append("Foreground app: ").append(pkg.ifBlank { "unknown" }).append('\n')
        sb.append("Screen: ").append(width()).append('x').append(height()).append(" px\n")
        val counter = intArrayOf(0)
        walk(root, sb, 0, counter)
        if (sb.length > 7000) {
            return sb.substring(0, 7000) + "\n... (screen truncated, scroll or narrow the view)"
        }
        return sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, counter: IntArray) {
        if (node == null || depth > 40 || counter[0] > 260 || sb.length > 8000) return
        val label = labelOf(node)
        val editable = node.isEditable
        val clickable = node.isClickable
        if (!label.isNullOrBlank() || editable || clickable) {
            val r = Rect()
            node.getBoundsInScreen(r)
            counter[0]++
            sb.append('[').append(counter[0]).append("] ")
            when {
                editable -> sb.append("field")
                clickable -> sb.append("button")
                else -> sb.append("text")
            }
            if (label != null) sb.append(" \"").append(label.replace("\n", " ")).append('"')
            if (editable && node.isFocused) sb.append(" (focused)")
            if (clickable && !editable) sb.append(" (tap)")
            if (r.width() > 0 && r.height() > 0) {
                sb.append(" @")
                    .append(fmt((r.left + r.right) / 2.0 / width()))
                    .append(',')
                    .append(fmt((r.top + r.bottom) / 2.0 / height()))
            }
            sb.append('\n')
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), sb, depth + 1, counter)
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String? {
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) return t
        val d = node.contentDescription?.toString()
        if (!d.isNullOrBlank()) return d
        val h = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) node.hintText?.toString() else null
        } catch (e: Exception) {
            null
        }
        return h?.takeIf { it.isNotBlank() }
    }

    private fun fmt(v: Double): String = String.format("%.3f", v)

    fun width(): Int = resources.displayMetrics.widthPixels

    fun height(): Int = resources.displayMetrics.heightPixels

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    // ---------------------------------------------------------------- tapping

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        collect(root, out, 0)
        return out
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (node == null || depth > 40 || out.size > 400) return
        out.add(node)
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    fun tapText(target: String): String {
        val t = target.trim()
        if (t.isEmpty()) return "No text given to tap."
        val nodes = allNodes()
        if (nodes.isEmpty()) return "No readable window right now."

        val lower = t.lowercase()
        var exact: AccessibilityNodeInfo? = null
        var partial: AccessibilityNodeInfo? = null
        for (n in nodes) {
            val l = labelOf(n)?.lowercase() ?: continue
            if (l == lower) { exact = clickableTarget(n); break }
            if (partial == null && l.contains(lower)) partial = clickableTarget(n)
        }
        val target = exact ?: partial ?: return "Nothing on screen matched \"$t\"."
        return if (tapNode(target)) "Tapped \"$t\"." else "Found \"$t\" but the tap did not register."
    }

    private fun clickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            hops++
        }
        return node
    }

    private fun tapNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return gestureTap(r.exactCenterX(), r.exactCenterY())
    }

    fun tapPoint(fx: Double, fy: Double): String {
        val x = (fx.coerceIn(0.0, 1.0) * width()).toFloat()
        val y = (fy.coerceIn(0.0, 1.0) * height()).toFloat()
        return if (gestureTap(x, y)) "Tapped at ${fmt(fx)},${fmt(fy)}." else "Tap gesture was rejected."
    }

    private fun gestureTap(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(direction: String, distance: Double): String {
        val w = width().toFloat()
        val h = height().toFloat()
        val d = distance.coerceIn(0.15, 0.9).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dy = h * d / 2f
        val dx = w * d / 2f
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> listOf(cx, cy + dy, cx, cy - dy)
            "down" -> listOf(cx, cy - dy, cx, cy + dy)
            "left" -> listOf(cx + dx, cy, cx - dx, cy)
            "right" -> listOf(cx - dx, cy, cx + dx, cy)
            else -> return "Unknown direction. Use up, down, left or right."
        }
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return if (dispatchGesture(gesture, null, null)) "Swiped $direction." else "Swipe gesture was rejected."
    }

    // ---------------------------------------------------------------- typing

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "No readable window right now."
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = focused?.takeIf { it.isEditable } ?: firstEditable(root)
        if (target == null) return "No text field is focused. Tap the field first, then type."
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "Typed into the field." else "The field refused the text. Try tapping it first."
    }

    private fun firstEditable(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = firstEditable(node.getChild(i), depth + 1)
            if (found != null) return found
        }
        return null
    }

    fun press(button: String): String {
        val action = when (button.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return "Unknown button. Use back, home, recents or notifications."
        }
        return if (performGlobalAction(action)) "Pressed $button." else "The system refused that action."
    }

    fun pressEnter(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Enter needs Android 11 or newer."
        }
        val root = rootInActiveWindow ?: return "No readable window right now."
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "No focused field to press Enter on."
        val ok = focused.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        )
        return if (ok) "Pressed Enter." else "The field refused Enter."
    }

    companion object {
        @Volatile
        var instance: ScreenAgentService? = null
            private set

        val connected = MutableStateFlow(false)

        fun ready(): Boolean = instance != null
    }
}
