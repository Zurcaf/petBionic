package com.example.petbionic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvSessionState: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnHistory: Button
    private lateinit var btnWifi: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startBLEScan()
        } else {
            Toast.makeText(this, "BLE permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BleManager.init(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvSessionState = findViewById(R.id.tvSessionState)
        btnConnect = findViewById(R.id.btnConnect)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnHistory = findViewById(R.id.btnHistory)
        btnWifi = findViewById(R.id.btnWifi)

        updateUI(false)

        BleManager.onConnectionChanged = { connected ->
            runOnUiThread {
                updateUI(connected)
                if (connected) {
                    Toast.makeText(this, "Connected to PetBionic!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        BleManager.onStatusReceived = { status ->
            runOnUiThread {
                val clean = when {
                    status.contains("recording") -> "🔴 Recording..."
                    status.contains("syncing")   -> "🔄 Syncing to cloud..."
                    status.contains("done")      -> "✅ Sync complete!"
                    status.contains("idle")      -> "⚪ Idle"
                    status.contains("sync_failed") -> "❌ Sync failed"
                    else -> status
                }
                tvSessionState.text = "State: $clean"
            }
        }

        btnConnect.setOnClickListener {
            if (BleManager.isConnected) {
                BleManager.disconnect()
            } else {
                requestBlePermissions()
            }
        }

        btnStart.setOnClickListener {
            BleManager.sendCommand("START")
            tvSessionState.text = "State: recording"
        }

        btnStop.setOnClickListener {
            BleManager.sendCommand("STOP")
            tvSessionState.text = "State: syncing..."
        }

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnWifi.setOnClickListener {
            showWifiDialog()
        }
    }

    private fun updateUI(connected: Boolean) {
        tvStatus.text = if (connected) "Connected" else "Disconnected"
        tvStatus.setTextColor(
            if (connected)
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        btnConnect.text = if (connected) "Disconnect" else "Connect to PetBionic"
        btnStart.isEnabled = connected
        btnStop.isEnabled = connected
        btnWifi.isEnabled = connected
    }

    private fun showWifiDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Configure WiFi")
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val etSsid = android.widget.EditText(this)
        etSsid.hint = "WiFi Network Name (SSID)"
        layout.addView(etSsid)

        val etPass = android.widget.EditText(this)
        etPass.hint = "WiFi Password"
        etPass.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(etPass)

        builder.setView(layout)
        builder.setPositiveButton("Send") { _, _ ->
            val ssid = etSsid.text.toString()
            val pass = etPass.text.toString()
            if (ssid.isNotEmpty()) {
                BleManager.sendCommand("WIFI:$ssid:$pass")
                Toast.makeText(this, "WiFi credentials sent!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

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

        if (allGranted) startBLEScan()
        else permissionLauncher.launch(permissions)
    }

    private fun startBLEScan() {
        Toast.makeText(this, "Scanning for PetBionic...", Toast.LENGTH_SHORT).show()
        BleManager.startScan()
    }
}