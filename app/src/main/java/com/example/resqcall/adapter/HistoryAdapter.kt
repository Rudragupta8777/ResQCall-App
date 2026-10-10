package com.example.resqcall.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.resqcall.R
import com.example.resqcall.data.AlertData
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val alerts: List<AlertData>,
    private val onViewLocation: (Double, Double) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // Which cards are expanded to reveal "View Location" - tracked by alert id so it
    // survives RecyclerView recycling the item view during scroll.
    private val expandedIds = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContent: View = view.findViewById(R.id.cardContent)
        val dateText: TextView = view.findViewById(R.id.alertDate)
        val statusPill: View = view.findViewById(R.id.alertStatus)
        val statusIcon: ImageView = view.findViewById(R.id.alertStatusIcon)
        val statusText: TextView = view.findViewById(R.id.alertStatusText)
        val resolvedByRow: View = view.findViewById(R.id.resolvedByRow)
        val resolvedBy: TextView = view.findViewById(R.id.resolvedByText)
        val locationRow: View = view.findViewById(R.id.locationRow)
        val btnLocation: MaterialButton = view.findViewById(R.id.btnViewLocation)
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
            val resolvedColor = android.graphics.Color.parseColor("#16A34A")
            holder.statusText.text = "Resolved"
            holder.statusIcon.setImageResource(R.drawable.ic_check)
            holder.statusPill.setBackgroundResource(R.drawable.pill_success_container)
            holder.statusText.setTextColor(resolvedColor)
            holder.statusIcon.setColorFilter(resolvedColor)
            holder.resolvedByRow.visibility = View.VISIBLE
            holder.resolvedBy.text = "Resolved by ${alert.resolvedBy?.name ?: "Caregiver"}"
        } else {
            val alertColor = android.graphics.Color.parseColor("#DC2626")
            holder.statusText.text = "Fall Detected"
            holder.statusIcon.setImageResource(R.drawable.ic_warning)
            holder.statusPill.setBackgroundResource(R.drawable.pill_error_container)
            holder.statusText.setTextColor(alertColor)
            holder.statusIcon.setColorFilter(alertColor)
            holder.resolvedByRow.visibility = View.GONE
        }

        holder.locationRow.visibility = if (expandedIds.contains(alert._id)) View.VISIBLE else View.GONE

        holder.cardContent.setOnClickListener {
            if (expandedIds.contains(alert._id)) {
                expandedIds.remove(alert._id)
            } else {
                expandedIds.add(alert._id)
            }
            notifyItemChanged(holder.adapterPosition)
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
