package com.example.isitdown.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.isitdown.MonitorService
import com.example.isitdown.NetworkUtils
import com.example.isitdown.R
import com.example.isitdown.data.LogRepository
import com.example.isitdown.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logAdapter: LogAdapter
    private lateinit var logRepository: LogRepository
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status")
            binding.tvStatus.text = getString(R.string.status_label, status)
            refreshLogs()
            
            if (status == "Idle") {
                resetUiToIdle()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logRepository = LogRepository(this)
        logAdapter = LogAdapter()

        setupRecyclerView()
        setupListeners()
        setupIntervalSpinner()
        checkPermissions()
        refreshLogs()
        
        // Prune logs on startup
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val retention = prefs.getLong(SettingsActivity.KEY_RETENTION, 604800000L)
        logRepository.pruneLogs(retention)
    }

    private fun setupIntervalSpinner() {
        val adapter = android.widget.ArrayAdapter.createFromResource(
            this,
            R.array.interval_options,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerInterval.adapter = adapter
        binding.spinnerInterval.setSelection(2) // Default to 5 Minutes (Index 2)
    }

    override fun onResume() {
        super.onResume()
        // Register receiver with explicit export flag for Android 14+
        val filter = IntentFilter("com.example.isitdown.UPDATE_STATUS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun setupRecyclerView() {
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
    }

    private fun setupListeners() {
        binding.btnStartStop.setOnClickListener {
            val currentText = binding.btnStartStop.text.toString()
            if (currentText == getString(R.string.start_monitoring)) {
                startMonitoring()
            } else {
                stopMonitoring()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnClearLogs.setOnClickListener {
            logRepository.clearLogs()
            refreshLogs()
            Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startMonitoring() {
        val host = binding.etHost.text.toString().trim()
        if (host.isEmpty()) {
            binding.inputLayout.error = "Please enter a host"
            return
        }
        
        if (!NetworkUtils.isValidHost(host)) {
            binding.inputLayout.error = "Invalid format. Must start with https://"
            return
        }
        
        binding.inputLayout.error = null
        
        // Get Settings
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val audioUri = prefs.getString(SettingsActivity.KEY_AUDIO_URI, null)
        val alertFreq = prefs.getInt(SettingsActivity.KEY_ALERT_FREQ, 1)

        // Get Interval
        // Map spinner index to value manually since we can't easily read integer-array from non-XML context perfectly without typed array
        val selectedIndex = binding.spinnerInterval.selectedItemPosition
        val intervals = resources.getIntArray(R.array.interval_values_ms)
        val interval = if (selectedIndex in intervals.indices) intervals[selectedIndex].toLong() else 300000L

        val intent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
            putExtra(MonitorService.EXTRA_HOST, host)
            putExtra(MonitorService.EXTRA_AUDIO_URI, audioUri)
            putExtra(MonitorService.EXTRA_ALERT_FREQ, alertFreq)
            putExtra(MonitorService.EXTRA_INTERVAL, interval)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        binding.btnStartStop.text = getString(R.string.stop_monitoring)
        binding.etHost.isEnabled = false
        binding.spinnerInterval.isEnabled = false
    }

    private fun stopMonitoring() {
        val intent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        startService(intent)
        // UI reset will happen via broadcast, but immediate feedback is good too
        resetUiToIdle()
    }

    private fun resetUiToIdle() {
        binding.btnStartStop.text = getString(R.string.start_monitoring)
        binding.etHost.isEnabled = true
        binding.spinnerInterval.isEnabled = true
        // Avoid overwriting "Idle" status if it's already set by receiver, 
        // but resetting it to Idle label safely is fine if we are sure we are stopped.
    }

    private fun refreshLogs() {
        uiScope.launch {
            val logs = withContext(Dispatchers.IO) {
                logRepository.readLogs()
            }
            logAdapter.submitList(logs)
            if (logs.isNotEmpty()) {
                binding.rvLogs.scrollToPosition(0)
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
