package com.example.isitdown.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.isitdown.R
import com.example.isitdown.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "IsItDownPrefs"
        const val KEY_AUDIO_URI = "audio_uri"
        const val KEY_AUDIO_NAME = "audio_name"
        const val KEY_ALERT_FREQ = "alert_freq" // 1 = Once, 2 = Twice, etc
        const val KEY_RETENTION = "log_retention" // Millis, -1 for forever
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = getFileName(it)
            prefs.edit().putString(KEY_AUDIO_URI, it.toString()).putString(KEY_AUDIO_NAME, name).apply()
            updateAudioUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupUi()
    }

    private fun setupUi() {
        // Audio
        updateAudioUi()
        binding.btnSelectAudio.setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*", "application/ogg")) 
            // Note: some audio files might have different mime types, "audio/*" is general
        }

        binding.btnResetAudio.setOnClickListener {
            prefs.edit().remove(KEY_AUDIO_URI).remove(KEY_AUDIO_NAME).apply()
            updateAudioUi()
        }

        // Frequency
        val freq = prefs.getInt(KEY_ALERT_FREQ, 3)
        when (freq) {
             2 -> binding.rbTwice.isChecked = true
             3 -> binding.rb3Times.isChecked = true
             else -> binding.rbOnce.isChecked = true
        }

        binding.rgFrequency.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.rbTwice -> 2
                R.id.rb3Times -> 3
                else -> 1
            }
            prefs.edit().putInt(KEY_ALERT_FREQ, value).apply()
        }

        // Retention
        val retention = prefs.getLong(KEY_RETENTION, 604800000L) // Default 1 week
        when (retention) {
            86400000L -> binding.rb24h.isChecked = true
            259200000L -> binding.rb3d.isChecked = true
            604800000L -> binding.rb1w.isChecked = true
            -1L -> binding.rbForever.isChecked = true
            else -> binding.rb1w.isChecked = true
        }

        binding.rgRetention.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.rb24h -> 86400000L
                R.id.rb3d -> 259200000L
                R.id.rb1w -> 604800000L
                R.id.rbForever -> -1L
                else -> 604800000L
            }
            prefs.edit().putLong(KEY_RETENTION, value).apply()
        }
    }

    private fun updateAudioUi() {
        val name = prefs.getString(KEY_AUDIO_NAME, "Default")
        binding.tvSelectedAudio.text = "Current: $name"
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(index >= 0) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "Unknown"
    }
}
