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
    private val onHistory: (MonitoredUser) -> Unit,
    private val onVitals: (MonitoredUser) -> Unit
) : RecyclerView.Adapter<WearerAdapter.ViewHolder>() {

    // Live BPM pushed in over the socket, keyed by wearerId - purely a display cache,
    // not part of the MonitoredUser model that comes from the REST sync.
    private val liveBpm = mutableMapOf<String, Int>()

    // Wearers currently reported offline by the "device_status" socket event - cleared again
    // the moment a subsequent "online" status (or heartbeat) comes in for that same wearer.
    private val offlineIds = mutableSetOf<String>()

    // Which cards are currently expanded to show the Fall History / Health Vitals actions.
    // Tracked by id (not view state) so it survives RecyclerView recycling the item view.
    private val expandedIds = mutableSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContent: View = view.findViewById(R.id.cardContent)
        val name: TextView = view.findViewById(R.id.wearerName)
        val battery: TextView = view.findViewById(R.id.batteryPercent)
        val pulseDot: View = view.findViewById(R.id.pulseDot)
        val emergencyActions: View = view.findViewById(R.id.emergencyActions)
        val btnResolve: View = view.findViewById(R.id.btnResolve)
        val btnDirections: View = view.findViewById(R.id.btnDirections)
        val card: CardView = view.findViewById(R.id.statusCard)
        val lastSeen: TextView = view.findViewById(R.id.lastSeen)
        val bpmText: TextView = view.findViewById(R.id.bpmText)
        val avatarInitial: TextView = view.findViewById(R.id.avatarInitial)
        val actionsRow: View = view.findViewById(R.id.actionsRow)
        val btnFallHistory: View = view.findViewById(R.id.btnFallHistory)
        val btnHealthVitals: View = view.findViewById(R.id.btnHealthVitals)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wearer_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        val displayName = if (!item.nickname.isNullOrEmpty()) item.nickname else item.wearer.name
        holder.name.text = displayName
        holder.avatarInitial.text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.battery.text = "${item.wearer.myWearable?.batteryLevel ?: 0}%"

        val bpm = liveBpm[item.wearer._id]
        holder.bpmText.text = if (bpm != null) "$bpm bpm" else "-- bpm"

        // Handle Active Alert UI State
        val hasActiveAlert = item.activeAlert != null && !item.activeAlert.resolved
        val isOffline = offlineIds.contains(item.wearer._id)
        when {
            hasActiveAlert -> {
                holder.emergencyActions.visibility = View.VISIBLE
                holder.card.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
                holder.pulseDot.setBackgroundResource(R.drawable.circle_red)
                holder.lastSeen.text = "Fall detected"
            }
            isOffline -> {
                holder.emergencyActions.visibility = View.GONE
                holder.card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                holder.pulseDot.setBackgroundResource(R.drawable.circle_grey)
                holder.lastSeen.text = "Offline"
            }
            else -> {
                holder.emergencyActions.visibility = View.GONE
                holder.card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                holder.pulseDot.setBackgroundResource(R.drawable.circle_green)
                holder.lastSeen.text = "Pulse: Active Now"
            }
        }

        // During an active alert, the card only ever shows the emergency actions -
        // Fall History / Health Vitals are hidden even if the card was tapped to expand.
        holder.actionsRow.visibility =
            if (!hasActiveAlert && expandedIds.contains(item.wearer._id)) View.VISIBLE else View.GONE

        // Pulse animation only makes sense while the dot represents something "live" -
        // a static grey dot communicates offline more clearly than a pulsing one.
        if (isOffline) {
            holder.pulseDot.clearAnimation()
        } else if (holder.pulseDot.animation == null) {
            val anim = AlphaAnimation(0.2f, 1.0f).apply {
                duration = 1000
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            holder.pulseDot.startAnimation(anim)
        }

        // Tapping the card elongates it to reveal the Fall History / Health Vitals actions.
        holder.cardContent.setOnClickListener {
            val wearerId = item.wearer._id
            if (expandedIds.contains(wearerId)) {
                expandedIds.remove(wearerId)
            } else {
                expandedIds.add(wearerId)
            }
            notifyItemChanged(holder.adapterPosition)
        }

        holder.cardContent.setOnLongClickListener {
            onLongClick(item)
            true
        }

        holder.btnResolve.setOnClickListener { onResolve(item) }

        holder.btnDirections.setOnClickListener {
            item.activeAlert?.location?.let { loc ->
                onDirections(loc.lat, loc.lon)
            }
        }

        holder.btnFallHistory.setOnClickListener { onHistory(item) }
        holder.btnHealthVitals.setOnClickListener { onVitals(item) }
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

    fun setOffline(wearerId: String, offline: Boolean) {
        val changed = if (offline) offlineIds.add(wearerId) else offlineIds.remove(wearerId)
        if (changed) {
            val index = items.indexOfFirst { it.wearer._id == wearerId }
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
    }

    fun clearActiveAlert(wearerId: String) {
        val index = items.indexOfFirst { it.wearer._id == wearerId }
        if (index != -1) {
            items[index] = items[index].copy(activeAlert = null)
            notifyItemChanged(index)
        }
    }

    override fun getItemCount() = items.size
}
