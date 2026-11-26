package com.wwdablu.dailyexpense.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Indian Rupee formatting, e.g. ₹1,23,456.50 (Indian digit grouping). */
private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    this.currency = Currency.getInstance("INR")
}

fun formatMoney(minor: Long): String = currency.format(BigDecimal.valueOf(minor, 2))

/** Parses user input like "12", "12.5", "12.50" into minor units; null if invalid or <= 0. */
fun parseMoney(text: String): Long? = runCatching {
    val value = BigDecimal(text.trim().replace(",", "."))
    if (value.signum() <= 0 || value.scale() > 2) null
    else value.movePointRight(2).longValueExact()
}.getOrNull()

fun minorToInput(minor: Long): String = BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

fun formatDay(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
