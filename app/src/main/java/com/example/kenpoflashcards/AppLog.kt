package com.example.kenpoflashcards

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple in-app debug log. Captures recent events/errors with context.
 * Accessible from the Admin → Debug Log screen.
 */
object AppLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val detail: String = ""   // extra context, stack trace, response body, etc.
    ) {
        val timeStr: String get() {
            val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return fmt.format(Date(timestamp))
        }
        val dateTimeStr: String get() {
            val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
            return fmt.format(Date(timestamp))
        }
    }

    private const val MAX_ENTRIES = 200
    private val _entries = ArrayDeque<Entry>()

    /** All log entries, newest first. */
    val entries: List<Entry> get() = synchronized(_entries) { _entries.reversed() }

    fun d(tag: String, message: String, detail: String = "") = add(Level.DEBUG, tag, message, detail)
    fun i(tag: String, message: String, detail: String = "") = add(Level.INFO,  tag, message, detail)
    fun w(tag: String, message: String, detail: String = "") = add(Level.WARN,  tag, message, detail)
    fun e(tag: String, message: String, detail: String = "") = add(Level.ERROR, tag, message, detail)

    private fun add(level: Level, tag: String, message: String, detail: String) {
        synchronized(_entries) {
            _entries.addLast(Entry(level = level, tag = tag, message = message, detail = detail))
            while (_entries.size > MAX_ENTRIES) _entries.removeFirst()
        }
        // Mirror to Android logcat
        when (level) {
            Level.DEBUG -> android.util.Log.d(tag, message)
            Level.INFO  -> android.util.Log.i(tag, message)
            Level.WARN  -> android.util.Log.w(tag, message)
            Level.ERROR -> android.util.Log.e(tag, if (detail.isNotBlank()) "$message\n$detail" else message)
        }
    }

    fun clear() = synchronized(_entries) { _entries.clear() }

    /** Copy all entries to a plain-text string for sharing. */
    fun export(): String = buildString {
        appendLine("=== Advanced Flashcards Debug Log ===")
        appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        appendLine()
        synchronized(_entries) {
            for (e in _entries.reversed()) {
                appendLine("[${e.dateTimeStr}] ${e.level.name.padEnd(5)} [${e.tag}] ${e.message}")
                if (e.detail.isNotBlank()) {
                    for (line in e.detail.lines()) appendLine("  $line")
                }
            }
        }
    }
}
