package com.wwdablu.dailyexpense.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wwdablu.dailyexpense.R
import com.wwdablu.dailyexpense.data.AppDatabase
import com.wwdablu.dailyexpense.data.AppSettings
import com.wwdablu.dailyexpense.data.ExpenseRepository
import com.wwdablu.dailyexpense.notify.AppIntents
import com.wwdablu.dailyexpense.notify.Notifications
import com.wwdablu.dailyexpense.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the once-a-day "log your expenses" notification. Uses a windowed alarm (no special
 * exact-alarm permission), so it arrives within about 10 minutes after the chosen time.
 */
object DailyReminder {
    private const val ACTION_FIRE = "com.wwdablu.dailyexpense.REMINDER"

    /** Sets (or cancels) the next reminder from the current settings. Safe to call any time. */
    fun schedule(context: Context) {
        val settings = AppSettings.get(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = firePendingIntent(context)
        alarms.cancel(pending)
        if (!settings.reminderEnabled.value) return
        val at = nextTrigger(settings.reminderMinutes.value, LocalDateTime.now())
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A 10-minute window: close to the chosen time without the exact-alarm permission.
        // (Plain inexact alarms may be delayed by up to an hour.)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, millis, WINDOW_MILLIS, pending)
    }

    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    /** The next time-of-day [minutes] strictly after [now]. */
    fun nextTrigger(minutes: Int, now: LocalDateTime): LocalDateTime {
        val todayAt = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60))
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    private fun firePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun showNotification(context: Context) {
        if (!Notifications.canPost(context)) return
        Notifications.ensureChannels(context)
        val today = LocalDate.now().toEpochDay()
        val totals = ExpenseRepository(AppDatabase.get(context)).observePeriodTotals(today, today).first()
        val text = if (totals.expense == 0L && totals.income == 0L) {
            "Did you spend anything today? Tap to log it."
        } else {
            "You've logged ${formatMoney(totals.expense)} spent today. Anything else to add?"
        }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle("Log today's expenses")
            .setContentText(text)
            .setContentIntent(AppIntents.open(context, add = false))
            .addAction(0, "Add expense", AppIntents.open(context, add = true))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by canPost above
        NotificationManagerCompat.from(context).notify(Notifications.ID_REMINDER, notification)
    }

    internal fun isFireAction(intent: Intent) = intent.action == ACTION_FIRE
}

/** Shows the reminder when the alarm fires, and re-arms it after reboots, app updates and clock changes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (DailyReminder.isFireAction(intent)) DailyReminder.showNotification(context)
                DailyReminder.schedule(context)
            } finally {
                result.finish()
            }
        }
    }
}
