package com.example.petbionic

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.FirebaseFirestore

class SessionDetailActivity : AppCompatActivity() {

    private lateinit var lineChartForce: LineChart
    private lateinit var lineChartAccel: LineChart
    private lateinit var tvSessionTitle: TextView
    private lateinit var tvStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_detail)

        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        tvStats = findViewById(R.id.tvStats)
        lineChartForce = findViewById(R.id.lineChartForce)
        lineChartAccel = findViewById(R.id.lineChartAccel)

        val sessionId = intent.getStringExtra("sessionId") ?: return
        tvSessionTitle.text = sessionId

        setupChart(lineChartForce, "Force (N)", Color.rgb(156, 39, 176))
        setupChart(lineChartAccel, "Acceleration", Color.rgb(33, 150, 243))

        loadSessionData(sessionId)
    }

    private fun setupChart(chart: LineChart, label: String, color: Int) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true
        chart.axisRight.isEnabled = false

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
    }

    private fun loadSessionData(sessionId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("sessions").document(sessionId)
            .collection("readings")
            .get()
            .addOnSuccessListener { documents ->
                val forceEntries = mutableListOf<Entry>()
                val accelEntries = mutableListOf<Entry>()

                var totalForce = 0f
                var maxForce = 0f
                var count = 0

                for (doc in documents) {
                    val raw = doc.getString("raw") ?: continue
                    val parts = raw.split(",")
                    if (parts.size < 3) continue

                    val index = count.toFloat()

                    when (parts[1].trim()) {
                        "HX" -> {
                            val force = parts[2].trim().toFloatOrNull() ?: continue
                            forceEntries.add(Entry(index, force))
                            totalForce += force
                            if (force > maxForce) maxForce = force
                        }
                        "IMU" -> {
                            val ax = parts[2].trim().toFloatOrNull() ?: 0f
                            val ay = parts[3].trim().toFloatOrNull() ?: 0f
                            val az = parts[4].trim().toFloatOrNull() ?: 0f
                            val magnitude = Math.sqrt(
                                (ax * ax + ay * ay + az * az).toDouble()
                            ).toFloat()
                            accelEntries.add(Entry(index, magnitude))
                        }
                    }
                    count++
                }

                // Update stats
                val avgForce = if (forceEntries.isNotEmpty())
                    totalForce / forceEntries.size else 0f
                tvStats.text = "Readings: $count  |  " +
                        "Avg Force: ${"%.2f".format(avgForce)}  |  " +
                        "Max Force: ${"%.2f".format(maxForce)}"

                // Plot force chart
                if (forceEntries.isNotEmpty()) {
                    val forceDataSet = LineDataSet(forceEntries, "Force").apply {
                        color = Color.rgb(156, 39, 176)
                        setCircleColor(Color.rgb(156, 39, 176))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }
                    lineChartForce.data = LineData(forceDataSet)
                    lineChartForce.invalidate()
                }

                // Plot acceleration chart
                if (accelEntries.isNotEmpty()) {
                    val accelDataSet = LineDataSet(accelEntries, "Acceleration magnitude").apply {
                        color = Color.rgb(33, 150, 243)
                        setCircleColor(Color.rgb(33, 150, 243))
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                    }
                    lineChartAccel.data = LineData(accelDataSet)
                    lineChartAccel.invalidate()
                }

                if (count == 0) {
                    Toast.makeText(this, "No readings found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }
}