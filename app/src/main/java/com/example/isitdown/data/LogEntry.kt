package com.example.isitdown.data

data class LogEntry(
    val timestamp: Long,
    val message: String,
    val isDown: Boolean
)
