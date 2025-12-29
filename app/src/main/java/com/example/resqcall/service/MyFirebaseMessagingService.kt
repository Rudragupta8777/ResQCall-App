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
        Log.d("FCM", "Message received data: ${remoteMessage.data}")

        val type = remoteMessage.data["type"]

        // Since we are using "Data-only" messages, we pull Title and Body from the data map
        val title = remoteMessage.data["title"] ?: "🚨 Emergency Alert"
        val body = remoteMessage.data["body"] ?: "Action required immediately"

        when (type) {
            "FALL_DETECTED" -> {
                val wearerName = remoteMessage.data["wearerName"] ?: "Someone"
                val lat = remoteMessage.data["lat"] ?: "0.0"
                val lon = remoteMessage.data["lon"] ?: "0.0"
                val wearerPhone = remoteMessage.data["wearerPhone"] ?: ""


                // Pass the extracted title and body to the siren function
                triggerEmergencySiren(title, body, wearerName, lat, lon, wearerPhone)
            }
            "BATTERY_LOW" -> {
                showBatteryReminder(title, body)
            }
        }
    }

    private fun triggerEmergencySiren(title: String, body: String, name: String, lat: String, lon: String, wearerPhone: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "emergency_channel"

        // 1. Setup Channel with Alarm Sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(channelId, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical fall detection alerts"
                setSound(Uri.parse("android.resource://$packageName/${R.raw.alarm}"), audioAttributes)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Create Intent to open EmergencyActivity
        val intent = Intent(this, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("wearerName", name)
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("wearerPhone", wearerPhone)
            putExtra("type", "FALL_DETECTED")
        }

        // 3. Create PendingIntent
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Build Notification with Full Screen Intent
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title) // Displays personalized title (e.g., Nickname)
            .setContentText(body)   // Displays personalized body
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(100, builder.build())
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(101, builder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}