package com.example.resqcall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.resqcall.EmergencyActivity
import com.example.resqcall.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = remoteMessage.data["type"]
        val title = remoteMessage.data["title"] ?: "🚨 Alert"
        val body = remoteMessage.data["body"] ?: "Action required"

        when (type) {
            "FALL_DETECTED" -> {
                val wearerName = remoteMessage.data["wearerName"] ?: "Someone"
                val lat = remoteMessage.data["lat"] ?: "0.0"
                val lon = remoteMessage.data["lon"] ?: "0.0"
                val wearerPhone = remoteMessage.data["wearerPhone"] ?: ""

                triggerEmergencySiren(title, body, wearerName, lat, lon, wearerPhone, type)
            }
            "DEVICE_OFFLINE" -> {
                // Offline is informational, not a life-threatening emergency - it must never
                // use the full-screen alarm siren reserved for FALL_DETECTED.
                val lat = remoteMessage.data["lat"]?.toDoubleOrNull() ?: 0.0
                val lon = remoteMessage.data["lon"]?.toDoubleOrNull() ?: 0.0
                showDeviceOfflineAlert(title, body, lat, lon)
            }
            "BATTERY_LOW" -> {
                showBatteryReminder(title, body)
            }
        }
    }

    private fun triggerEmergencySiren(title: String, body: String, name: String, lat: String, lon: String, wearerPhone: String, type: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "emergency_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(channelId, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Uri.parse("android.resource://$packageName/${R.raw.alarm}"), audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("wearerName", name)
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("wearerPhone", wearerPhone)
            putExtra("type", type)
        }

        val pendingIntent = PendingIntent.getActivity(this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setOngoing(true)

        notificationManager.notify(100, builder.build())
    }

    private fun showDeviceOfflineAlert(title: String, body: String, lat: Double, lon: Double) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "device_offline_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Device Offline Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // Tapping the notification drops a pin at the device's last known location.
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$lat,$lon(Last Known Location)"))
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(102, builder.build())
    }

    private fun showBatteryReminder(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "reminder_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.battery_low)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
        notificationManager.notify(101, builder.build())
    }
}