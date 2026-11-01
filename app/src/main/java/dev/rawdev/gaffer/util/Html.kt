package dev.rawdev.gaffer.util

object Html {

    private val scriptStyle = Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>")
    private val tag = Regex("(?s)<[^>]+>")
    private val ws = Regex("[ \\t\\x0B\\f\\r]+")
    private val blank = Regex("\\n{3,}")

    fun toText(html: String): String {
        var s = scriptStyle.replace(html, " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
        s = tag.replace(s, " ")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&mdash;", "-")
            .replace("&ndash;", "-")
        s = ws.replace(s, " ")
        s = s.lines().joinToString("\n") { it.trim() }
        s = blank.replace(s, "\n\n")
        return s.trim()
    }
}
