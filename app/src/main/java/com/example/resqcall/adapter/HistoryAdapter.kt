package com.example.resqcall.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resqcall.R
import com.example.resqcall.data.AlertData
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val alerts: List<AlertData>,
    private val onViewLocation: (Double, Double) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.alertDate)
        val statusText: TextView = view.findViewById(R.id.alertStatus)
        val resolvedBy: TextView = view.findViewById(R.id.resolvedByText)
        val btnLocation: Button = view.findViewById(R.id.btnViewLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alerts[position]

        holder.dateText.text = formatTimestamp(alert.timestamp)

        if (alert.resolved) {
            holder.statusText.text = "✅ Alert Resolved"
            holder.statusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.resolvedBy.visibility = View.VISIBLE
            holder.resolvedBy.text = "Resolved by: ${alert.resolvedBy?.name ?: "Caregiver"}"
        } else {
            holder.statusText.text = "🚨 Unresolved Fall"
            holder.statusText.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            holder.resolvedBy.visibility = View.GONE
        }

        holder.btnLocation.setOnClickListener {
            onViewLocation(alert.location.lat, alert.location.lon)
        }
    }

    override fun getItemCount() = alerts.size

    private fun formatTimestamp(isoString: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(isoString)
            val formatter = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            date?.let { formatter.format(it) } ?: isoString
        } catch (e: Exception) {
            isoString
        }
    }
}