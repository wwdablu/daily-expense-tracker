package com.wwdablu.dailyexpense.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.wwdablu.dailyexpense.R
import com.wwdablu.dailyexpense.data.AppDatabase
import com.wwdablu.dailyexpense.data.AppSettings
import com.wwdablu.dailyexpense.data.ExpenseRepository
import com.wwdablu.dailyexpense.notify.AppIntents
import com.wwdablu.dailyexpense.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Today" home-screen widget: amount spent today, the daily limit and what's left of it
 * (when a monthly budget is set), and a + button that opens the app on "add entry".
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { update(context, manager, ids) } finally { result.finish() }
        }
    }

    override fun onDisabled(context: Context) {
        // Last widget removed: stop the midnight refresh.
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    companion object {
        /** Redraws every placed widget. Call after entries or the budget change. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { update(app, manager, ids) }
        }

        /** Asks the launcher to place the widget (Android shows its own confirmation). */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, TodayWidget::class.java), null, null)
        }

        private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val today = LocalDate.now()
            val epochDay = today.toEpochDay()
            val totals = ExpenseRepository(AppDatabase.get(context)).observePeriodTotals(epochDay, epochDay).first()
            val budget = AppSettings.get(context).budget.value
            val spent = totals.expense.coerceAtLeast(0)  // refunds never take it below ₹0

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_spent, formatMoney(spent))
            if (budget != null) {
                val limit = budget / today.lengthOfMonth()
                val left = limit - spent
                if (left >= 0) {
                    views.setTextViewText(R.id.widget_detail, "${formatMoney(left)} left of ${formatMoney(limit)}")
                    views.setTextColor(R.id.widget_detail, ContextCompat.getColor(context, R.color.widget_accent))
                } else {
                    views.setTextViewText(R.id.widget_detail, "Over daily limit by ${formatMoney(-left)}")
                    views.setTextColor(R.id.widget_detail, ContextCompat.getColor(context, R.color.widget_over))
                }
            } else {
                views.setTextViewText(
                    R.id.widget_detail,
                    if (totals.income > 0) "Earned ${formatMoney(totals.income)} today" else "Tap + to add an expense",
                )
                views.setTextColor(R.id.widget_detail, ContextCompat.getColor(context, R.color.widget_subtext))
            }
            views.setOnClickPendingIntent(R.id.widget_root, AppIntents.open(context, add = false))
            views.setOnClickPendingIntent(R.id.widget_add, AppIntents.open(context, add = true))
            ids.forEach { manager.updateAppWidget(it, views) }

            scheduleMidnightRefresh(context)
        }

        /** Refresh just after midnight so "today" rolls over to the new day. */
        private fun scheduleMidnightRefresh(context: Context) {
            val at = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)
                .set(AlarmManager.RTC, at, midnightIntent(context))
        }

        private fun midnightIntent(context: Context): PendingIntent {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            val intent = Intent(context, TodayWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            return PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
