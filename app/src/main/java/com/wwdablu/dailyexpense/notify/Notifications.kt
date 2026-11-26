package com.wwdablu.dailyexpense.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Notifications {
    const val CHANNEL_REMINDER = "daily_reminder"
    const val CHANNEL_SMS = "sms_detected"

    const val ID_REMINDER = 1001
    // SMS notifications use ID_SMS_BASE + the detected entry's id.
    const val ID_SMS_BASE = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A daily nudge to log your expenses" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SMS, "Detected from SMS", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Expenses and earnings found in bank SMS" }
        )
    }

    /** Android 13+ needs the POST_NOTIFICATIONS permission; earlier versions allow by default. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
