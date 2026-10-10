package com.example.resqcall.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.resqcall.R
import com.example.resqcall.data.MonitoredUser

class WearerAdapter(
    private val items: MutableList<MonitoredUser>,
    private val onLongClick: (MonitoredUser) -> Unit,
    private val onResolve: (MonitoredUser) -> Unit,
    private val onDirections: (Double, Double) -> Unit,
    private val onCardClick: (MonitoredUser) -> Unit, // Added this parameter
    private val onVitals: (MonitoredUser) -> Unit
) : RecyclerView.Adapter<WearerAdapter.ViewHolder>() {

    // Live BPM pushed in over the socket, keyed by wearerId - purely a display cache,
    // not part of the MonitoredUser model that comes from the REST sync.
    private val liveBpm = mutableMapOf<String, Int>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.wearerName)
        val battery: TextView = view.findViewById(R.id.batteryPercent)
        val pulseDot: View = view.findViewById(R.id.pulseDot)
        val emergencyActions: View = view.findViewById(R.id.emergencyActions)
        val btnResolve: View = view.findViewById(R.id.btnResolve)
        val btnDirections: View = view.findViewById(R.id.btnDirections)
        val card: CardView = view.findViewById(R.id.statusCard)
        val bpmPill: View = view.findViewById(R.id.bpmPill)
        val bpmText: TextView = view.findViewById(R.id.bpmText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wearer_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = if (!item.nickname.isNullOrEmpty()) item.nickname else item.wearer.name
        holder.battery.text = "${item.wearer.myWearable?.batteryLevel ?: 0}%"

        val bpm = liveBpm[item.wearer._id]
        holder.bpmText.text = if (bpm != null) "$bpm bpm" else "-- bpm"

        // Handle Active Alert UI State
        if (item.activeAlert != null && !item.activeAlert.resolved) {
            holder.emergencyActions.visibility = View.VISIBLE
            holder.card.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            holder.pulseDot.setBackgroundResource(R.drawable.circle_red)
        } else {
            holder.emergencyActions.visibility = View.GONE
            holder.card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            holder.pulseDot.setBackgroundResource(R.drawable.circle_green)
        }

        // Pulse Animation
        if (holder.pulseDot.animation == null) {
            val anim = AlphaAnimation(0.2f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            holder.pulseDot.startAnimation(anim)
        }

        // SET CLICK LISTENER FOR THE CARD
        holder.itemView.setOnClickListener { onCardClick(item) }

        // Click Listeners
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }

        holder.btnResolve.setOnClickListener { onResolve(item) }

        holder.btnDirections.setOnClickListener {
            item.activeAlert?.location?.let { loc ->
                onDirections(loc.lat, loc.lon)
            }
        }

        holder.bpmPill.setOnClickListener { onVitals(item) }
    }

    fun updateBattery(wearerId: String, newLevel: Int) {
        val index = items.indexOfFirst { it.wearer._id == wearerId }
        if (index != -1) {
            val currentItem = items[index]
            val updatedWearer = currentItem.wearer.copy(
                myWearable = currentItem.wearer.myWearable?.copy(batteryLevel = newLevel)
            )
            items[index] = currentItem.copy(wearer = updatedWearer)
            notifyItemChanged(index)
        }
    }

    fun updateLiveBpm(wearerId: String, bpm: Int) {
        liveBpm[wearerId] = bpm
        val index = items.indexOfFirst { it.wearer._id == wearerId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    override fun getItemCount() = items.size
}
