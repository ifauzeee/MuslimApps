package com.example.muslimapps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AdhanBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "Adzan"
            showAdhanNotification(it, prayerName)
        }
    }

    private fun showAdhanNotification(context: Context, prayerName: String) {
        createNotificationChannel(context)

        val notificationBuilder = NotificationCompat.Builder(context, "adhan_channel_id")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ganti dengan ikon notifikasi Anda
            .setContentTitle("Waktu Shalat Telah Tiba!")
            .setContentText("$prayerName telah tiba. Mari menunaikan shalat.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Adzan Reminders"
            val descriptionText = "Channel for Adzan prayer time reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("adhan_channel_id", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}