package com.dailyexpense.tracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the maximum monthly budget (in paise). null = no budget set. Applies to every month. */
class BudgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _budget = MutableStateFlow(prefs.getLong(KEY, 0L).takeIf { it > 0 })
    val budget: StateFlow<Long?> = _budget.asStateFlow()

    fun set(minor: Long?) {
        prefs.edit().apply { if (minor == null) remove(KEY) else putLong(KEY, minor) }.apply()
        _budget.value = minor
    }

    private companion object { const val KEY = "monthly_budget_minor" }
}
