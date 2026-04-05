package com.example.petbionic

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.android.material.chip.Chip
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TIME_SYNC_RETRY_INTERVAL_MS = 5000L
        private const val STATUS_REFRESH_INTERVAL_MS = 2000L
        private const val DISPLAY_SAMPLE_RATE_HZ = 80.0
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvSessionState: TextView
    private lateinit var tvTimeSyncState: TextView
    private lateinit var tvSamplesState: TextView
    private lateinit var viewConnectionDot: View
    private lateinit var chipSd: Chip
    private lateinit var chipImu: Chip
    private lateinit var chipHx711: Chip
    private lateinit var chipWifi: Chip
    private lateinit var btnConnect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnHistory: Button
    private lateinit var btnSendSession: Button
    private lateinit var btnWifi: Button

    private lateinit var prefs: SharedPreferences

    private var lastTimeSyncAttemptElapsedMs: Long = 0
    private var lastSamplesUiValue: Long = -1
    private var runStartElapsedMs: Long = 0
    private var latestAcquisitionEnabled: Boolean = false
    private var pendingSessionCommand: String? = null
    private var statusJsonBuffer: String = ""
    private var finalRunDialogShowing: Boolean = false

    private val statusRefreshHandler = Handler(Looper.getMainLooper())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            if (!BleManager.isConnected) return
            maybeSendTimeSync(force = false)
            BleManager.requestStatusRefresh()
            statusRefreshHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
        }
    }

    private val samplesInterpolationRunnable = object : Runnable {
        override fun run() {
            if (!BleManager.isConnected) return
            updateInterpolatedSamples()
            statusRefreshHandler.postDelayed(this, 100L)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startBLEScan()
        else Toast.makeText(this, "BLE permissions required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BleManager.init(this)
        prefs = getSharedPreferences("petbionic_prefs", MODE_PRIVATE)

        tvStatus          = findViewById(R.id.tvStatus)
        tvSessionState    = findViewById(R.id.tvSessionState)
        tvTimeSyncState   = findViewById(R.id.tvTimeSyncState)
        tvSamplesState    = findViewById(R.id.tvSamplesState)
        viewConnectionDot = findViewById(R.id.viewConnectionDot)
        chipSd            = findViewById(R.id.chipSd)
        chipImu           = findViewById(R.id.chipImu)
        chipHx711         = findViewById(R.id.chipHx711)
        chipWifi          = findViewById(R.id.chipWifi)
        btnConnect        = findViewById(R.id.btnConnect)
        btnStart          = findViewById(R.id.btnStartStop)
        btnStop           = btnStart  // Same button, toggles based on state
        btnHistory        = findViewById(R.id.btnHistory)
        btnSendSession    = findViewById(R.id.btnSendSession)
        btnWifi           = findViewById(R.id.btnWifi)

        // Show last-known SSID from prefs before BLE connects
        prefs.getString("pref_wifi_ssid", null)?.let { savedSsid ->
            if (savedSsid.isNotEmpty()) setWifiChip(ssid = savedSsid, enabled = null)
        }

        BleManager.onConnectionChanged = { connected ->
            runOnUiThread {
                updateUI(connected)
                if (connected) {
                    sendCurrentTimeToDevice()
                    startStatusRefreshLoop()
                    startSamplesInterpolationLoop()
                    Toast.makeText(this, "Connected to PetBionic!", Toast.LENGTH_SHORT).show()
                } else {
                    stopStatusRefreshLoop()
                    stopSamplesInterpolationLoop()
                    pendingSessionCommand = null
                    latestAcquisitionEnabled = false
                    runStartElapsedMs = 0
                    statusJsonBuffer = ""
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        BleManager.onStatusReceived = { status ->
            runOnUiThread { renderStatus(status) }
        }

        val connectedNow = BleManager.isConnected
        updateUI(connectedNow)
        if (connectedNow) {
            BleManager.getLastStatusSnapshot()?.let { renderStatus(it) }
            BleManager.requestStatusRefresh()
            startStatusRefreshLoop()
            startSamplesInterpolationLoop()
        } else {
            stopStatusRefreshLoop()
            stopSamplesInterpolationLoop()
            resetSamplesDisplay()
        }

        btnConnect.setOnClickListener {
            if (BleManager.isConnected) BleManager.disconnect()
            else requestBlePermissions()
        }

        btnStart.setOnClickListener {
            if (latestAcquisitionEnabled || pendingSessionCommand.equals("START", ignoreCase = true)) {
                pendingSessionCommand = "STOP"
                tvSessionState.text = "Stopping..."
                BleManager.sendCommand("STOP")
            } else {
                pendingSessionCommand = "START"
                runStartElapsedMs = 0
                resetSamplesDisplay(initialValue = 0)
                tvSessionState.text = "Starting..."
                BleManager.sendCommand("START")
            }
            requestStatusRefreshBurst()
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnSendSession.setOnClickListener {
            BleManager.sendCommand("SYNC")
            Toast.makeText(this, "Requesting cloud sync...", Toast.LENGTH_SHORT).show()
        }

        btnWifi.setOnClickListener {
            showWifiDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (BleManager.isConnected) {
            startStatusRefreshLoop()
            startSamplesInterpolationLoop()
        }
    }

    override fun onPause() {
        super.onPause()
        stopStatusRefreshLoop()
        stopSamplesInterpolationLoop()
    }

    // ── UI helpers ─────────────────────────────────────────────────────────

    private fun updateUI(connected: Boolean) {
        // Connection dot colour
        val dotColor = if (connected)
            ContextCompat.getColor(this, R.color.pb_primary)
        else
            ContextCompat.getColor(this, R.color.pb_secondary)
        viewConnectionDot.backgroundTintList = ColorStateList.valueOf(dotColor)

        tvStatus.text = if (connected) "Connected" else "Disconnected"
        tvStatus.setTextColor(
            if (connected) ContextCompat.getColor(this, R.color.pb_primary)
            else ContextCompat.getColor(this, R.color.pb_on_background)
        )

        btnConnect.text = if (connected) "Disconnect" else getString(R.string.action_connect)
        btnConnect.backgroundTintList = ColorStateList.valueOf(
            if (connected) ContextCompat.getColor(this, R.color.pb_secondary)
            else ContextCompat.getColor(this, R.color.pb_primary)
        )

        btnStart.isEnabled         = connected
        btnStop.isEnabled          = connected
        btnSendSession.isEnabled   = connected
        btnWifi.isEnabled          = connected
        btnWifi.alpha              = if (connected) 0.70f else 0.35f

        if (!connected) {
            tvSessionState.text = "—"
            tvSessionState.setTextColor(ContextCompat.getColor(this, R.color.pb_on_background))
            tvSamplesState.visibility = View.INVISIBLE
            tvTimeSyncState.text = getString(R.string.state_time_sync_unknown)
            resetSensorChips()
        }
    }

    private fun setSensorChip(chip: Chip, ready: Boolean?) {
        val (bg, fg, label) = when (ready) {
            true  -> Triple(
                ContextCompat.getColor(this, R.color.pb_primary),
                ContextCompat.getColor(this, R.color.pb_on_primary),
                chip.text.toString().trimEnd(' ', '✓', '✗') + " ✓"
            )
            false -> Triple(
                ContextCompat.getColor(this, R.color.pb_error),
                ContextCompat.getColor(this, R.color.pb_on_error),
                chip.text.toString().trimEnd(' ', '✓', '✗') + " ✗"
            )
            null  -> Triple(
                ContextCompat.getColor(this, R.color.pb_surface_variant),
                ContextCompat.getColor(this, R.color.pb_on_surface),
                chip.text.toString().trimEnd(' ', '✓', '✗')
            )
        }
        chip.chipBackgroundColor = ColorStateList.valueOf(bg)
        chip.setTextColor(fg)
        chip.text = label
    }

    private fun resetSensorChips() {
        for (chip in listOf(chipSd, chipImu, chipHx711)) {
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.pb_surface_variant)
            )
            chip.setTextColor(ContextCompat.getColor(this, R.color.pb_on_surface))
            chip.text = chip.text.toString().trimEnd(' ', '✓', '✗')
        }
        // WiFi chip: keep last-known SSID from prefs if available, but reset colour
        chipWifi.chipBackgroundColor = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.pb_surface_variant)
        )
        chipWifi.setTextColor(ContextCompat.getColor(this, R.color.pb_on_surface))
        val savedSsid = prefs.getString("pref_wifi_ssid", null)
        chipWifi.text = if (!savedSsid.isNullOrEmpty()) "WiFi: $savedSsid" else "WiFi"
    }

    /** Update the WiFi chip to reflect device-reported state.
     *  [enabled] = true → green (configured & device accepted it)
     *  [enabled] = false → error red (explicitly disabled)
     *  [enabled] = null → neutral (unknown / pre-connect)
     */
    private fun setWifiChip(ssid: String?, enabled: Boolean?) {
        val label = if (!ssid.isNullOrEmpty()) "WiFi: $ssid" else "WiFi"
        val (bg, fg) = when (enabled) {
            true  -> Pair(
                ContextCompat.getColor(this, R.color.pb_primary),
                ContextCompat.getColor(this, R.color.pb_on_primary)
            )
            false -> Pair(
                ContextCompat.getColor(this, R.color.pb_surface_variant),
                ContextCompat.getColor(this, R.color.pb_on_surface)
            )
            null  -> Pair(
                ContextCompat.getColor(this, R.color.pb_surface_variant),
                ContextCompat.getColor(this, R.color.pb_on_surface)
            )
        }
        chipWifi.chipBackgroundColor = ColorStateList.valueOf(bg)
        chipWifi.setTextColor(fg)
        chipWifi.text = label
    }

    private fun startStatusRefreshLoop() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
        statusRefreshHandler.post(statusRefreshRunnable)
    }

    private fun stopStatusRefreshLoop() {
        statusRefreshHandler.removeCallbacks(statusRefreshRunnable)
    }

    private fun startSamplesInterpolationLoop() {
        statusRefreshHandler.removeCallbacks(samplesInterpolationRunnable)
        statusRefreshHandler.post(samplesInterpolationRunnable)
    }

    private fun stopSamplesInterpolationLoop() {
        statusRefreshHandler.removeCallbacks(samplesInterpolationRunnable)
    }

    private fun requestStatusRefreshBurst() {
        statusRefreshHandler.postDelayed({ BleManager.requestStatusRefresh() }, 120)
        statusRefreshHandler.postDelayed({ BleManager.requestStatusRefresh() }, 300)
    }

    private fun resetSamplesDisplay(initialValue: Long = 0L) {
        lastSamplesUiValue = initialValue
        tvSamplesState.text = "Samples: $initialValue"
    }

    private fun updateInterpolatedSamples() {
        if (finalRunDialogShowing) return
        val runActive = latestAcquisitionEnabled ||
                pendingSessionCommand.equals("START", ignoreCase = true)
        if (!runActive || runStartElapsedMs <= 0L) return
        val elapsedMs  = (System.currentTimeMillis() - runStartElapsedMs).coerceAtLeast(0L)
        val displayVal = (elapsedMs * DISPLAY_SAMPLE_RATE_HZ / 1000.0).toLong()
        if (displayVal != lastSamplesUiValue) {
            lastSamplesUiValue = displayVal
            tvSamplesState.text = "Samples: $displayVal"
        }
    }

    private fun maybeSendTimeSync(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastTimeSyncAttemptElapsedMs) < TIME_SYNC_RETRY_INTERVAL_MS) return
        sendCurrentTimeToDevice()
    }

    private fun sendCurrentTimeToDevice() {
        BleManager.sendCommand("TIME=${System.currentTimeMillis()}")
        lastTimeSyncAttemptElapsedMs = System.currentTimeMillis()
    }

    // ── JSON handling ──────────────────────────────────────────────────────

    private fun showFinalRunDialog(json: JSONObject) {
        if (finalRunDialogShowing) return
        Log.i("MainActivity", "showFinalRunDialog: $json")

        val runName      = json.optString("run_name", "run")
        val samplesFinal = json.optLong("samples_final", json.optLong("samples", 0L))
        val durationMs   = json.optLong("duration_ms", 0L)
        val imuFailure   = json.optBoolean("imu_failure", false)
        val hx711Failure = json.optBoolean("hx711_failure", false)
        val syncPending  = json.optBoolean("sync_pending", false)
        val durationSec  = durationMs / 1000.0

        val message = buildString {
            appendLine("Run: $runName")
            appendLine("Samples: $samplesFinal")
            appendLine("Duration: ${"%.1f".format(durationSec)} s")
            if (imuFailure)   appendLine("⚠ IMU fault during run")
            if (hx711Failure) appendLine("⚠ Load cell fault during run")
            if (syncPending)  appendLine("☁  Syncing to cloud...")
        }

        finalRunDialogShowing = true
        android.app.AlertDialog.Builder(this)
            .setTitle("Run complete")
            .setMessage(message.trim())
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finalRunDialogShowing = false
                pendingSessionCommand = null
                latestAcquisitionEnabled = false
                runStartElapsedMs = 0
                resetSamplesDisplay(initialValue = 0)
                BleManager.requestStatusRefresh()
            }
            .show()
    }

    private fun handleStatusJson(json: JSONObject): Boolean {
        // ── sync_result notification (sent as standalone JSON by firmware) ──
        val syncResult = json.optString("sync_result", "")
        if (syncResult.isNotEmpty()) {
            val msg = when (syncResult) {
                "ok"         -> "☁ Session uploaded successfully!"
                "no_wifi"    -> "WiFi not configured on device"
                "no_session" -> "No previous session to sync"
                else         -> "⚠ Upload failed — data safe on SD card"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return true
        }

        if (json.optBoolean("run_complete", false)) {
            Log.i("MainActivity", "run_complete received")
            latestAcquisitionEnabled = false
            showFinalRunDialog(json)
            return true
        }

        val acq      = json.optBoolean("acq", false)
        val sd       = json.optBoolean("sd", false)
        val imu      = if (json.has("imu"))   json.optBoolean("imu",   false) else null
        val hx711    = if (json.has("hx711")) json.optBoolean("hx711", false) else null
        val syncNeeded = json.optBoolean("time_sync_needed", false)
        val timeSynced = json.optBoolean("time_synced", false)
        val cmdAck   = json.optString("cmd_ack", "")

        // WiFi status from device
        if (json.has("wifi_enabled")) {
            val wifiEnabled = json.optBoolean("wifi_enabled", false)
            val wifiSsid    = json.optString("wifi_ssid", "")
            setWifiChip(ssid = wifiSsid.ifEmpty { null }, enabled = if (wifiEnabled) true else null)
            if (wifiEnabled && wifiSsid.isNotEmpty()) {
                prefs.edit().putString("pref_wifi_ssid", wifiSsid).apply()
            }
        }

        Log.d("MainActivity", "status acq=$acq sd=$sd cmdAck='$cmdAck'")
        latestAcquisitionEnabled = acq

        if (cmdAck.equals("START", ignoreCase = true)) {
            runStartElapsedMs = System.currentTimeMillis()
            Log.i("MainActivity", "START ack – interpolation clock anchored")
            resetSamplesDisplay(initialValue = 0)
        } else if (acq && runStartElapsedMs == 0L) {
            runStartElapsedMs = System.currentTimeMillis()
        }

        if (cmdAck.equals("START", ignoreCase = true) || acq) {
            if (pendingSessionCommand.equals("START", ignoreCase = true)) {
                pendingSessionCommand = null
            }
        }
        if (cmdAck.equals("STOP", ignoreCase = true) || !acq) {
            if (pendingSessionCommand.equals("STOP", ignoreCase = true)) {
                pendingSessionCommand = null
            }
        }

        if (syncNeeded) maybeSendTimeSync(force = false)

        // Session state label + colour
        val (sessionText, sessionColor) = when {
            pendingSessionCommand.equals("START", ignoreCase = true) && !acq ->
                Pair("Starting...", ContextCompat.getColor(this, R.color.pb_on_background))
            pendingSessionCommand.equals("STOP", ignoreCase = true) && acq ->
                Pair("Stopping...", ContextCompat.getColor(this, R.color.pb_on_background))
            acq  -> Pair("● Recording", ContextCompat.getColor(this, R.color.pb_primary))
            else -> Pair("Idle", ContextCompat.getColor(this, R.color.pb_on_background))
        }
        tvSessionState.text = sessionText
        tvSessionState.setTextColor(sessionColor)

        tvSamplesState.visibility = if (acq) View.VISIBLE else View.INVISIBLE

        // Sensor chips
        setSensorChip(chipSd,    sd)
        setSensorChip(chipImu,   imu)
        setSensorChip(chipHx711, hx711)

        // Time sync
        tvTimeSyncState.text = when {
            syncNeeded  -> "⏱ Time sync needed"
            timeSynced  -> "⏱ Time synced"
            else        -> getString(R.string.state_time_sync_unknown)
        }

        return true
    }

    private fun tryConsumeJsonPayloads(cleaned: String): Boolean {
        if (cleaned.isEmpty()) return false
        statusJsonBuffer += cleaned
        if (statusJsonBuffer.length > 4096) {
            statusJsonBuffer = statusJsonBuffer.takeLast(4096)
        }
        var handledAny = false
        while (true) {
            val start = statusJsonBuffer.indexOf('{')
            if (start < 0) { statusJsonBuffer = ""; break }
            var depth = 0; var end = -1
            for (i in start until statusJsonBuffer.length) {
                when (statusJsonBuffer[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { end = i; break } }
                }
            }
            if (end < 0) { if (start > 0) statusJsonBuffer = statusJsonBuffer.substring(start); break }
            val payload = statusJsonBuffer.substring(start, end + 1)
            statusJsonBuffer = statusJsonBuffer.substring(end + 1)
            val parsed = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            if (handleStatusJson(parsed)) handledAny = true
        }
        return handledAny
    }

    private fun renderStatus(status: String) {
        val cleaned = status.replace("\u0000", "").trim()
        Log.d("MainActivity", "renderStatus(${cleaned.length}): ${cleaned.take(120)}")
        if (tryConsumeJsonPayloads(cleaned)) return
        if (cleaned.startsWith("{") || cleaned.startsWith("\"")) return
        val legacyState = when {
            cleaned.contains("recording", ignoreCase = true) -> "Recording"
            cleaned.contains("idle", ignoreCase = true)      -> "Idle"
            else -> return
        }
        tvSessionState.text = legacyState
    }

    // ── WiFi dialog ────────────────────────────────────────────────────────

    private fun showWifiDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        val etSsid = android.widget.EditText(this).apply { hint = "WiFi Network (SSID)" }
        val etPass = android.widget.EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etSsid)
        layout.addView(etPass)

        // Pre-fill SSID if we already know it
        prefs.getString("pref_wifi_ssid", null)?.let { etSsid.setText(it) }

        android.app.AlertDialog.Builder(this)
            .setTitle("Configure WiFi Sync")
            .setMessage("Credentials are sent to the device via BLE and used to upload sessions after each run.")
            .setView(layout)
            .setPositiveButton("Send") { _, _ ->
                val ssid = etSsid.text.toString().trim()
                val pass = etPass.text.toString()
                if (ssid.isNotEmpty()) {
                    BleManager.sendCommand("WIFI=$ssid:$pass")
                    prefs.edit().putString("pref_wifi_ssid", ssid).apply()
                    setWifiChip(ssid = ssid, enabled = null)
                    Toast.makeText(this, "WiFi credentials sent to device", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SSID cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Permissions ────────────────────────────────────────────────────────

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startBLEScan() else permissionLauncher.launch(permissions)
    }

    private fun startBLEScan() {
        Toast.makeText(this, "Scanning for PetBionic...", Toast.LENGTH_SHORT).show()
        BleManager.startScan()
    }
}
