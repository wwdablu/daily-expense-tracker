package com.wwdablu.dailyexpense.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide settings, kept in SharedPreferences. One shared instance so the app, the reminder,
 * the widget and the SMS receiver all see the same values.
 */
class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Maximum monthly budget in paise; null = no budget. Applies to every month. */
    private val _budget = MutableStateFlow(prefs.getLong(KEY_BUDGET, 0L).takeIf { it > 0 })
    val budget: StateFlow<Long?> = _budget.asStateFlow()

    private val _reminderEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMINDER_ON, false))
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    /** Reminder time as minutes after midnight (default 9:00 PM). */
    private val _reminderMinutes = MutableStateFlow(prefs.getInt(KEY_REMINDER_MIN, 21 * 60))
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    private val _smsDetection = MutableStateFlow(prefs.getBoolean(KEY_SMS_ON, false))
    val smsDetection: StateFlow<Boolean> = _smsDetection.asStateFlow()

    fun setBudget(minor: Long?) {
        prefs.edit().apply { if (minor == null) remove(KEY_BUDGET) else putLong(KEY_BUDGET, minor) }.apply()
        _budget.value = minor
    }

    fun setReminderEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ON, on).apply()
        _reminderEnabled.value = on
    }

    fun setReminderMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMINDER_MIN, minutes).apply()
        _reminderMinutes.value = minutes
    }

    fun setSmsDetection(on: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_ON, on).apply()
        _smsDetection.value = on
    }

    companion object {
        private const val KEY_BUDGET = "monthly_budget_minor"
        private const val KEY_REMINDER_ON = "reminder_enabled"
        private const val KEY_REMINDER_MIN = "reminder_minutes"
        private const val KEY_SMS_ON = "sms_detection_enabled"

        @Volatile private var instance: AppSettings? = null
        fun get(context: Context): AppSettings = instance ?: synchronized(this) {
            instance ?: AppSettings(context).also { instance = it }
        }
    }
}
