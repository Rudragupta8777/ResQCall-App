package com.example.resqcall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.resqcall.api.RetrofitClient
import com.example.resqcall.data.CriticalEvent
import com.example.resqcall.data.VitalsDay
import com.example.resqcall.util.showError
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class VitalsActivity : AppCompatActivity() {

    private lateinit var wearerId: String
    private lateinit var wearerName: String

    private lateinit var lineChart: LineChart
    private lateinit var barChart: BarChart
    private lateinit var chartEmptyText: TextView
    private lateinit var statMin: TextView
    private lateinit var statMax: TextView
    private lateinit var statAvg: TextView
    private lateinit var criticalEventsContainer: android.widget.LinearLayout
    private lateinit var noCriticalEventsText: TextView
    private lateinit var loading: CircularProgressIndicator
    private lateinit var locationLabel: TextView
    private lateinit var locationUpdatedAt: TextView
    private lateinit var btnDirections: MaterialButton

    private var currentRange = "today"
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vitals)

        wearerId = intent.getStringExtra("WEARER_ID") ?: run { finish(); return }
        wearerName = intent.getStringExtra("WEARER_NAME") ?: "Wearer"

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "$wearerName · Vitals"
        toolbar.setNavigationOnClickListener { finish() }

        lineChart = findViewById(R.id.lineChart)
        barChart = findViewById(R.id.barChart)
        chartEmptyText = findViewById(R.id.chartEmptyText)
        statMin = findViewById(R.id.statMin)
        statMax = findViewById(R.id.statMax)
        statAvg = findViewById(R.id.statAvg)
        criticalEventsContainer = findViewById(R.id.criticalEventsContainer)
        noCriticalEventsText = findViewById(R.id.noCriticalEventsText)
        loading = findViewById(R.id.vitalsLoading)
        locationLabel = findViewById(R.id.locationLabel)
        locationUpdatedAt = findViewById(R.id.locationUpdatedAt)
        btnDirections = findViewById(R.id.btnDirections)

        setupCharts()

        btnDirections.setOnClickListener {
            val lat = lastLat
            val lon = lastLon
            if (lat != null && lon != null) {
                val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            } else {
                showError("Location not available yet")
            }
        }

        findViewById<TabLayout>(R.id.rangeTabs).addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentRange = when (tab.position) {
                    1 -> "week"
                    2 -> "month"
                    else -> "today"
                }
                fetchVitals(currentRange)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        fetchLocation()
        fetchVitals(currentRange)
    }

    private fun setupCharts() {
        lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            axisRight.isEnabled = false
            axisLeft.textColor = android.graphics.Color.parseColor("#64748B")
            axisLeft.gridColor = android.graphics.Color.parseColor("#E2E8F0")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = android.graphics.Color.parseColor("#64748B")
            xAxis.gridColor = android.graphics.Color.parseColor("#F1F5F9")
            xAxis.setLabelCount(5, false)
        }
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(true)
            axisRight.isEnabled = false
            axisLeft.textColor = android.graphics.Color.parseColor("#64748B")
            axisLeft.gridColor = android.graphics.Color.parseColor("#E2E8F0")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = android.graphics.Color.parseColor("#64748B")
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
        }
    }

    private fun fetchLocation() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getWearerLocation(wearerId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val loc = body?.location
                    if (loc != null) {
                        lastLat = loc.lat
                        lastLon = loc.lon
                        locationLabel.text = "Last known location"
                        locationUpdatedAt.text = loc.updatedAt?.let { "Updated ${formatTimestamp(it)}" } ?: ""
                    } else {
                        locationLabel.text = "Location unavailable"
                    }
                } else {
                    locationLabel.text = "Location unavailable"
                }
            } catch (e: Exception) {
                locationLabel.text = "Location unavailable"
            }
        }
    }

    private fun fetchVitals(range: String) {
        loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getWearerVitals(wearerId, range)
                loading.visibility = View.GONE
                if (response.isSuccessful) {
                    val days = response.body() ?: emptyList()
                    renderStats(days)
                    renderCriticalEvents(days)
                    if (range == "today") {
                        renderTodayChart(days)
                    } else {
                        renderRangeChart(days)
                    }
                } else {
                    showError("Couldn't load vitals (${response.code()})")
                    renderStats(emptyList())
                    renderCriticalEvents(emptyList())
                }
            } catch (e: Exception) {
                loading.visibility = View.GONE
                showError("Network error loading vitals")
            }
        }
    }

    private fun renderStats(days: List<VitalsDay>) {
        if (days.isEmpty()) {
            statMin.text = "--"
            statMax.text = "--"
            statAvg.text = "--"
            return
        }
        val min = days.minOf { it.stats.minBpm }
        val max = days.maxOf { it.stats.maxBpm }
        val avg = days.sumOf { it.stats.avgBpm } / days.size
        statMin.text = min.toString()
        statMax.text = max.toString()
        statAvg.text = avg.toString()
    }

    private fun renderTodayChart(days: List<VitalsDay>) {
        barChart.visibility = View.GONE
        val readings = days.firstOrNull()?.readings
        if (readings.isNullOrEmpty()) {
            lineChart.visibility = View.GONE
            chartEmptyText.visibility = View.VISIBLE
            return
        }
        chartEmptyText.visibility = View.GONE
        lineChart.visibility = View.VISIBLE

        val entries = readings.mapIndexed { index, reading -> Entry(index.toFloat(), reading.bpm.toFloat()) }
        val timeFormatter = timeOnlyFormatter()
        val labels = readings.map { timeFormatter.format(parseIso(it.timestamp) ?: java.util.Date()) }

        val dataSet = LineDataSet(entries, "BPM").apply {
            color = android.graphics.Color.parseColor("#0D9488")
            setCircleColor(android.graphics.Color.parseColor("#0D9488"))
            circleRadius = 2.5f
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = android.graphics.Color.parseColor("#0D9488")
            fillAlpha = 30
        }

        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }

    private fun renderRangeChart(days: List<VitalsDay>) {
        lineChart.visibility = View.GONE
        if (days.isEmpty()) {
            barChart.visibility = View.GONE
            chartEmptyText.visibility = View.VISIBLE
            return
        }
        chartEmptyText.visibility = View.GONE
        barChart.visibility = View.VISIBLE

        val minEntries = days.mapIndexed { index, day -> BarEntry(index.toFloat(), day.stats.minBpm.toFloat()) }
        val maxEntries = days.mapIndexed { index, day -> BarEntry(index.toFloat(), day.stats.maxBpm.toFloat()) }
        val labels = days.map { it.dateString.takeLast(5) }

        val minSet = BarDataSet(minEntries, "Low").apply {
            color = android.graphics.Color.parseColor("#99D9D4")
        }
        val maxSet = BarDataSet(maxEntries, "High").apply {
            color = android.graphics.Color.parseColor("#0D9488")
        }

        val groupSpace = 0.3f
        val barSpace = 0.02f
        val barWidth = 0.33f

        val data = BarData(minSet, maxSet)
        data.barWidth = barWidth

        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = 0f + days.size
        barChart.data = data
        barChart.groupBars(0f, groupSpace, barSpace)
        barChart.invalidate()
    }

    private fun renderCriticalEvents(days: List<VitalsDay>) {
        criticalEventsContainer.removeAllViews()
        val events = days.flatMap { it.criticalEvents }.sortedByDescending { it.timestamp }

        if (events.isEmpty()) {
            noCriticalEventsText.visibility = View.VISIBLE
            return
        }
        noCriticalEventsText.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (event in events) {
            val row = inflater.inflate(R.layout.item_critical_event, criticalEventsContainer, false)
            row.findViewById<TextView>(R.id.eventStatus).text = formatEventStatus(event.status)
            row.findViewById<TextView>(R.id.eventTime).text = formatTimestamp(event.timestamp)
            row.findViewById<TextView>(R.id.eventBpm).text = "${event.bpm} bpm"
            criticalEventsContainer.addView(row)
        }
    }

    private fun formatEventStatus(status: String): String {
        return status.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun parseIso(isoString: String): java.util.Date? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault())
                parser.timeZone = TimeZone.getTimeZone("UTC")
                return parser.parse(isoString)
            } catch (e: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun timeOnlyFormatter(): SimpleDateFormat {
        return SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    private fun formatTimestamp(isoString: String): String {
        val date = parseIso(isoString) ?: return isoString
        val formatter = SimpleDateFormat("MMM dd, yyyy • h:mm a", Locale.getDefault())
        return formatter.format(date)
    }
}
