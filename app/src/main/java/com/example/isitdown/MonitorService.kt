package com.example.isitdown

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.isitdown.data.LogRepository
import com.example.isitdown.ui.MainActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_HOST = "EXTRA_HOST"
        const val EXTRA_AUDIO_URI = "EXTRA_AUDIO_URI"
        const val EXTRA_ALERT_FREQ = "EXTRA_ALERT_FREQ" // 1 = Once, 2 = Twice

        const val EXTRA_INTERVAL = "EXTRA_INTERVAL" // Long millis

        const val CHANNEL_ID = "monitor_channel"
        const val NOTIFICATION_ID = 1
    }

    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var logRepository: LogRepository
    private var isMonitoring = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        logRepository = LogRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: return START_NOT_STICKY
                val audioUri = intent.getStringExtra(EXTRA_AUDIO_URI)
                val alertFreq = intent.getIntExtra(EXTRA_ALERT_FREQ, 1)
                val interval = intent.getLongExtra(EXTRA_INTERVAL, 300000L) // Default 5 min
                startMonitoring(host, audioUri, alertFreq, interval)
            }
            ACTION_STOP -> {
                stopMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring(host: String, audioUriStr: String?, alertFreq: Int, interval: Long) {
        if (isMonitoring) return
        isMonitoring = true

        val notification = createNotification("Monitoring $host...")
        startForeground(NOTIFICATION_ID, notification)

        serviceJob = serviceScope.launch {
            logRepository.writeLog("Started monitoring $host (every ${interval/60000}m)", false)
            broadcastStatus("Monitoring...")

            var wasDown: Boolean? = null // Tri-state: null (init), false, true

            while (isActive && isMonitoring) {
                val isInternet = NetworkUtils.isInternetAvailable()
                
                if (!isInternet) {
                    logRepository.writeLog("No Internet Connection", true)
                    broadcastStatus("No Internet")
                    val timestamp = getTimestamp()
                    updateNotification("Waiting for Internet...\nLast check: $timestamp", false)
                    wasDown = null // Reset so next status change triggers log/alert if needed
                } else {
                    val isReachable = NetworkUtils.isHostReachable(host)
                    val isDown = !isReachable
    
                    if (isDown) {
                        logRepository.writeLog("Host $host is DOWN", true)
                        broadcastStatus("DOWN")
                        
                        if (wasDown != true) {
                            // Changed to DOWN or Initial check
                            playAlert(audioUriStr, alertFreq)
                            val timestamp = getTimestamp()
                            updateNotification("Alert: $host is DOWN!\nLast check: $timestamp", true)
                        } else {
                            // Still down, maybe update timestamp?
                            val timestamp = getTimestamp()
                            updateNotification("Alert: $host is DOWN!\nLast check: $timestamp", true)
                        }
                    } else {
                        // is UP
                        if (wasDown != false) {
                             // Changed to UP or Initial check
                             logRepository.writeLog("Host $host is UP", false)
                             val timestamp = getTimestamp()
                             updateNotification("Server is UP: $host\nLast check: $timestamp", false)
                        }
                        // Periodic update? 
                        // Let's at least broadcast UP status every time
                        broadcastStatus("UP")
                        // Also update notification with latest time even if status didn't change
                        val timestamp = getTimestamp()
                        updateNotification("Server is UP: $host\nLast check: $timestamp", false)
                    }
                    wasDown = isDown
                }
                delay(interval)
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        serviceJob?.cancel()
        logRepository.writeLog("Monitoring stopped", false)
        broadcastStatus("Idle")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playAlert(audioUriStr: String?, freq: Int) {
        serviceScope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                    )
                    
                    if (!audioUriStr.isNullOrEmpty()) {
                        try {
                            setDataSource(applicationContext, Uri.parse(audioUriStr))
                        } catch (e: Exception) {
                            // Fallback to system default if custom fails
                             setDataSource(applicationContext, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                        }
                    } else {
                         // Default logic if no custom sound
                         setDataSource(applicationContext, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                    }

                    prepare()
                    start()
                }

                if (freq > 1) {
                    delay(3000) // Wait 3 sec
                    if (mediaPlayer?.isPlaying == false) {
                         mediaPlayer?.start()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Broadcast for UI updates - simplistic approach
    private fun broadcastStatus(status: String) {
        val intent = Intent("com.example.isitdown.UPDATE_STATUS")
        intent.putExtra("status", status)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync) // Generic icon
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", 
                PendingIntent.getService(this, 0, Intent(this, MonitorService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
            )
            .setOngoing(true)
            .build()
    }
    
    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun updateNotification(text: String, isAlert: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(if (isAlert) android.R.drawable.stat_notify_error else android.R.drawable.ic_popup_sync)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", 
                PendingIntent.getService(this, 0, Intent(this, MonitorService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
            )
            .setOngoing(true)
            
        // Use high importance for Down alert? Default is LOW for persistent service.
        // Maybe create a separate channel for ALERTS with HIGH importance?
        // keeping it simple for now.
        
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        mediaPlayer?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
