package com.example.isitdown

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NetworkUtils {

    fun isValidHost(host: String): Boolean {
        if (!host.startsWith("https://")) return false
        
        // Strip protocol to check domain validity
        val domain = host.substringAfter("https://").substringBefore("/")
        
        if (Patterns.DOMAIN_NAME.matcher(domain).matches()) return true
        if (domain == "localhost") return true
        
        return false
    }

    suspend fun isHostReachable(host: String): Boolean = withContext(Dispatchers.IO) {
        // Try ICMP Ping first (might require root on some Androids, but 'isReachable' tries best effort)
        try {
            val address = InetAddress.getByName(host)
            if (address.isReachable(3000)) return@withContext true
        } catch (e: Exception) {
            // Ignore and fall back to HTTP
        }

        // Fallback: Try HTTP connection (Port 80/443)
        // This is more reliable for web servers
        val urlString = host // Host already has https://
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD" // Just check headers
            val responseCode = connection.responseCode
            // 200-399 are generally success/redirect. 
            // 400+ might mean server is there but erroring, which technically means "UP" but for "IsItDown" usually implies we can reach it.
            // However, user might want to know if it's down-down.
            // Let's assume if we get ANY response code, the server is "UP".
            // If it throws exception, it's unconnected.
            return@withContext responseCode > 0
        } catch (e: IOException) {
            return@withContext false
        }
    }

    suspend fun isInternetAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1500)
            socket.close()
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }
}
