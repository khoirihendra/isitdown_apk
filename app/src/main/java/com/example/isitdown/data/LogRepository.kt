package com.example.isitdown.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRepository(private val context: Context) {

    private val fileName = "monitor_logs.txt"
    private val file by lazy { File(context.filesDir, fileName) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun writeLog(message: String, isDown: Boolean) {
        val timestamp = System.currentTimeMillis()
        val entryString = "$timestamp|$isDown|$message\n"
        try {
            file.appendText(entryString)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun readLogs(): List<LogEntry> {
        if (!file.exists()) return emptyList()
        val logs = mutableListOf<LogEntry>()
        try {
            file.readLines().reversed().forEach { line -> // Read new to old
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) {
                    logs.add(LogEntry(
                        timestamp = parts[0].toLongOrNull() ?: 0L,
                        isDown = parts[1].toBoolean(),
                        message = parts[2]
                    ))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return logs
    }

    fun clearLogs() {
        if (file.exists()) {
            file.writeText("")
        }
    }

    fun pruneLogs(retentionInMillis: Long) {
        if (retentionInMillis <= 0) return // Forever
        if (!file.exists()) return

        val cutoff = System.currentTimeMillis() - retentionInMillis
        val validLines = mutableListOf<String>()

        try {
            file.readLines().forEach { line ->
                val parts = line.split("|", limit = 3)
                if (parts.isNotEmpty()) {
                    val timestamp = parts[0].toLongOrNull() ?: 0L
                    if (timestamp >= cutoff) {
                        validLines.add(line)
                    }
                }
            }
            file.writeText(validLines.joinToString("\n") + if (validLines.isNotEmpty()) "\n" else "")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
