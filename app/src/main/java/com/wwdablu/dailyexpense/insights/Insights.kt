package com.wwdablu.dailyexpense.insights

import com.wwdablu.dailyexpense.data.CategoryTotal
import com.wwdablu.dailyexpense.ui.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line in the Insights card. [tone] picks the icon and colour. */
data class Insight(val text: String, val tone: Tone) {
    enum class Tone { Good, Bad, Neutral }
}

/** Spending totals for one day. */
data class DayTotal(val epochDay: Long, val total: Long)

/** Date ranges compared by the insights. When the period is still in progress, both ranges stop at the same point. */
data class InsightRanges(
    val isYear: Boolean,
    val start: LocalDate,
    /** Last day counted: today if the period is in progress, otherwise the period's last day. */
    val end: LocalDate,
    val inProgress: Boolean,
    /** Last day of the whole period (for the month-end projection). */
    val periodEnd: LocalDate,
    val prevStart: LocalDate,
    val prevEnd: LocalDate,
) {
    companion object {
        fun of(day: LocalDate, isYear: Boolean, today: LocalDate): InsightRanges {
            val start = if (isYear) day.withDayOfYear(1) else day.withDayOfMonth(1)
            val periodEnd = if (isYear) day.withDayOfYear(day.lengthOfYear()) else day.withDayOfMonth(day.lengthOfMonth())
            val inProgress = !today.isBefore(start) && !today.isAfter(periodEnd)
            val end = if (inProgress) today else periodEnd
            val prevStart = if (isYear) start.minusYears(1) else start.minusMonths(1)
            val prevPeriodEnd = if (isYear) prevStart.withDayOfYear(prevStart.lengthOfYear()) else prevStart.withDayOfMonth(prevStart.lengthOfMonth())
            // Same number of days into the previous period, capped at its end (e.g. 31 Mar vs 28 Feb).
            val elapsed = ChronoUnit.DAYS.between(start, end)
            val prevEnd = minOf(prevStart.plusDays(elapsed), prevPeriodEnd)
            return InsightRanges(isYear, start, end, inProgress, periodEnd, prevStart, prevEnd)
        }
    }
}

/** Spending numbers the insights are computed from. Amounts are in paise; only expenses, not earnings. */
data class InsightInputs(
    val ranges: InsightRanges,
    val spent: Long,
    val prevSpent: Long,
    val byType: List<CategoryTotal>,
    val prevByType: List<CategoryTotal>,
    val daily: List<DayTotal>,
    /** Monthly budget, if set. */
    val budget: Long?,
)

object InsightsCalculator {
    private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val MONTH = DateTimeFormatter.ofPattern("MMMM")
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun compute(i: InsightInputs): List<Insight> {
        val r = i.ranges
        if (i.spent == 0L) {
            val text = if (r.inProgress) "No spending recorded for this ${unit(r)} yet."
            else "No spending recorded in ${if (r.isYear) "${r.start.year}" else r.start.format(MONTH_YEAR)}."
            return listOf(Insight(text, Insight.Tone.Neutral))
        }
        val out = mutableListOf<Insight>()
        val prevName = if (r.isYear) "${r.prevStart.year}" else r.prevStart.format(MONTH)
        val sameDays = if (r.inProgress) "the same days of $prevName" else prevName

        // 1. Compared with the previous period
        if (i.prevSpent > 0) {
            val change = (i.spent - i.prevSpent) * 100.0 / i.prevSpent
            val pct = abs(change).roundToInt()
            out += when {
                pct == 0 -> Insight("About the same as $sameDays (${formatMoney(i.prevSpent)}).", Insight.Tone.Neutral)
                change > 0 -> Insight("$pct% more than $sameDays (${formatMoney(i.prevSpent)}).", Insight.Tone.Bad)
                else -> Insight("$pct% less than $sameDays (${formatMoney(i.prevSpent)}).", Insight.Tone.Good)
            }
        }

        // 2. Types that changed the most
        if (i.prevSpent > 0) {
            val prev = i.prevByType.associateBy { it.categoryId }
            // Refund types (negative totals) lower spending; a rise or drop in them isn't spending.
            val refundIds = (i.byType + i.prevByType).filter { it.total < 0 }.map { it.categoryId }.toSet()
            val keys = (i.byType.map { it.categoryId } + i.prevByType.map { it.categoryId }).distinct() - refundIds
            val diffs = keys.map { id ->
                val now = i.byType.firstOrNull { it.categoryId == id }
                val before = prev[id]
                Triple(now?.categoryName ?: before?.categoryName ?: "Uncategorized", now?.total ?: 0L, before?.total ?: 0L)
            }
            diffs.filter { it.second > it.third }.maxByOrNull { it.second - it.third }?.let { (name, now, before) ->
                out += Insight("Biggest rise: $name, up ${formatMoney(now - before)} on $prevName.", Insight.Tone.Bad)
            }
            diffs.filter { it.second < it.third }.maxByOrNull { it.third - it.second }?.let { (name, now, before) ->
                out += Insight("Biggest drop: $name, down ${formatMoney(before - now)} on $prevName.", Insight.Tone.Good)
            }
        }

        // 3. Highest day (month) or highest month (year)
        if (r.isYear) {
            i.daily.groupBy { LocalDate.ofEpochDay(it.epochDay).month }
                .mapValues { (_, days) -> days.sumOf { it.total } }
                .maxByOrNull { it.value }
                ?.let { (month, total) ->
                    val name = month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                    out += Insight("Biggest month: $name, ${formatMoney(total)}.", Insight.Tone.Neutral)
                }
        } else {
            i.daily.maxByOrNull { it.total }?.let {
                out += Insight("Highest day: ${LocalDate.ofEpochDay(it.epochDay).format(DAY)}, ${formatMoney(it.total)}.", Insight.Tone.Neutral)
            }
        }

        // 4. Average, and days without spending (month only)
        val days = ChronoUnit.DAYS.between(r.start, r.end) + 1
        if (r.isYear) {
            // Average over months that have spending, so months before the app was used don't drag it down.
            val months = i.daily.filter { it.total > 0 }.map { LocalDate.ofEpochDay(it.epochDay).monthValue }.distinct().size
            if (months > 0) {
                out += Insight(
                    "Average ${formatMoney(i.spent / months)} a month over $months ${if (months == 1) "month" else "months"} with spending.",
                    Insight.Tone.Neutral,
                )
            }
        } else {
            val noSpend = days - i.daily.count { it.total > 0 }
            val avg = "Average ${formatMoney(i.spent / days)} a day"
            out += Insight(if (noSpend > 0) "$avg; $noSpend ${if (noSpend == 1L) "day" else "days"} with no spending." else "$avg.", Insight.Tone.Neutral)
        }

        // 5. Month-end projection for the current month
        if (!r.isYear && r.inProgress && days >= 3) {
            val totalDays = ChronoUnit.DAYS.between(r.start, r.periodEnd) + 1
            val projected = i.spent * totalDays / days
            out += if (i.budget != null) {
                if (projected > i.budget) {
                    Insight("At this pace: about ${formatMoney(projected)} by month end, ${formatMoney(projected - i.budget)} over budget.", Insight.Tone.Bad)
                } else {
                    Insight("At this pace: about ${formatMoney(projected)} by month end, within budget.", Insight.Tone.Good)
                }
            } else {
                Insight("At this pace: about ${formatMoney(projected)} by month end.", Insight.Tone.Neutral)
            }
        }
        return out
    }

    private fun unit(r: InsightRanges) = if (r.isYear) "year" else "month"
}
