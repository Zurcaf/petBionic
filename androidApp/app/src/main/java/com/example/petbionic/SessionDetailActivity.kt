package com.example.petbionic

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.sqrt

class SessionDetailActivity : AppCompatActivity() {

    private lateinit var lineChartForce: LineChart
    private lateinit var lineChartAccel: LineChart
    private lateinit var tvSessionTitle: TextView
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarDetail)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        tvStats        = findViewById(R.id.tvStats)
        lineChartForce = findViewById(R.id.lineChartForce)
        lineChartAccel = findViewById(R.id.lineChartAccel)

        val sessionId = intent.getStringExtra("sessionId") ?: return
        tvSessionTitle.text = sessionId
        supportActionBar?.title = sessionId

        setupChart(lineChartForce, ContextCompat.getColor(this, R.color.pb_primary))
        setupChart(lineChartAccel, ContextCompat.getColor(this, R.color.pb_secondary))

        loadSessionData(sessionId)
    }

    private fun setupChart(chart: LineChart, color: Int) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true
        chart.axisRight.isEnabled = false

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 0.1f
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = "${"%.1f".format(value)}s"
        }
    }

    private fun loadSessionData(sessionId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions").document(sessionId)
            .collection("readings")
            .orderBy("t_rel_ms")
            .get()
            .addOnSuccessListener { documents ->
                val forceEntries = mutableListOf<Entry>()
                val accelEntries = mutableListOf<Entry>()

                var totalForce = 0f
                var maxForce   = 0f
                var count      = 0

                for (doc in documents) {
                    // x-axis: relative time in seconds
                    val tRelMs    = doc.getLong("t_rel_ms") ?: continue
                    val xSec      = tRelMs.toFloat() / 1000f

                    // Force: use filtered load cell value
                    val loadFilt  = doc.getDouble("load_cell_filt")?.toFloat() ?: continue

                    // Acceleration magnitude from raw IMU axes
                    val ax = doc.getDouble("imu_ax")?.toFloat() ?: 0f
                    val ay = doc.getDouble("imu_ay")?.toFloat() ?: 0f
                    val az = doc.getDouble("imu_az")?.toFloat() ?: 0f
                    val accelMag = sqrt(ax * ax + ay * ay + az * az)

                    forceEntries.add(Entry(xSec, loadFilt))
                    accelEntries.add(Entry(xSec, accelMag))

                    totalForce += loadFilt
                    if (loadFilt > maxForce) maxForce = loadFilt
                    count++
                }

                val avgForce = if (count > 0) totalForce / count else 0f
                tvStats.text = "$count readings  •  " +
                        "Avg: ${"%.1f".format(avgForce)}  •  " +
                        "Max: ${"%.1f".format(maxForce)}"

                if (forceEntries.isNotEmpty()) {
                    val ds = LineDataSet(forceEntries, "Load cell (filtered)").apply {
                        color = ContextCompat.getColor(this@SessionDetailActivity, R.color.pb_primary)
                        setCircleColor(ContextCompat.getColor(this@SessionDetailActivity, R.color.pb_primary))
                        lineWidth = 1.8f
                        circleRadius = 0f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }
                    lineChartForce.data = LineData(ds)
                    lineChartForce.invalidate()
                }

                if (accelEntries.isNotEmpty()) {
                    val ds = LineDataSet(accelEntries, "Acceleration magnitude").apply {
                        color = ContextCompat.getColor(this@SessionDetailActivity, R.color.pb_secondary)
                        setCircleColor(ContextCompat.getColor(this@SessionDetailActivity, R.color.pb_secondary))
                        lineWidth = 1.8f
                        circleRadius = 0f
                        setDrawCircles(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }
                    lineChartAccel.data = LineData(ds)
                    lineChartAccel.invalidate()
                }

                if (count == 0) {
                    Toast.makeText(this, "No readings found for this session", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load session data", Toast.LENGTH_SHORT).show()
            }
    }
}
