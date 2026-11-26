package com.wwdablu.dailyexpense.insights

import com.wwdablu.dailyexpense.data.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun t(id: Long, name: String, rupees: Long) = CategoryTotal(id, name, 0, null, rupees * 100)

    @Test fun rangesForMonthInProgressCompareSameDays() {
        val r = InsightRanges.of(today, isYear = false, today = today)
        assertEquals(LocalDate.of(2026, 11, 1), r.start)
        assertEquals(today, r.end)
        assertEquals(LocalDate.of(2026, 10, 1), r.prevStart)
        assertEquals(LocalDate.of(2026, 10, 26), r.prevEnd)
        assertTrue(r.inProgress)
    }

    @Test fun rangesForPastMonthUseWholeMonths() {
        val r = InsightRanges.of(LocalDate.of(2026, 3, 10), isYear = false, today = today)
        assertEquals(LocalDate.of(2026, 3, 31), r.end)
        assertEquals(LocalDate.of(2026, 2, 28), r.prevEnd) // capped at February's end
    }

    @Test fun monthInsights() {
        val r = InsightRanges.of(today, false, today)
        val out = InsightsCalculator.compute(
            InsightInputs(
                ranges = r,
                spent = 13_000_00, prevSpent = 10_000_00,
                byType = listOf(t(1, "Food", 8_000), t(2, "Shopping", 5_000)),
                prevByType = listOf(t(1, "Food", 4_000), t(2, "Shopping", 6_000)),
                daily = listOf(DayTotal(today.minusDays(1).toEpochDay(), 9_000_00), DayTotal(today.toEpochDay(), 4_000_00)),
                budget = 15_000_00,
            )
        ).map { it.text }
        assertEquals("30% more than the same days of October (₹10,000.00).", out[0])
        assertEquals("Biggest rise: Food, up ₹4,000.00 on October.", out[1])
        assertEquals("Biggest drop: Shopping, down ₹1,000.00 on October.", out[2])
        assertEquals("Highest day: Wed, 25 Nov, ₹9,000.00.", out[3])
        assertEquals("Average ₹500.00 a day; 24 days with no spending.", out[4])
        assertEquals("At this pace: about ₹15,000.00 by month end, within budget.", out[5])
    }

    @Test fun nothingSpentInPastYear() {
        val r = InsightRanges.of(LocalDate.of(2025, 6, 1), true, today)
        val out = InsightsCalculator.compute(InsightInputs(r, 0, 0, emptyList(), emptyList(), emptyList(), null))
        assertEquals(listOf("No spending recorded in 2025."), out.map { it.text })
    }

    @Test fun nothingSpent() {
        val r = InsightRanges.of(today, false, today)
        val out = InsightsCalculator.compute(InsightInputs(r, 0, 0, emptyList(), emptyList(), emptyList(), null))
        assertEquals(listOf("No spending recorded for this month yet."), out.map { it.text })
    }

    @Test fun yearInsights() {
        val r = InsightRanges.of(today, true, today)
        val out = InsightsCalculator.compute(
            InsightInputs(
                ranges = r, spent = 22_000_00, prevSpent = 0,
                byType = listOf(t(1, "Food", 22_000)), prevByType = emptyList(),
                daily = listOf(DayTotal(LocalDate.of(2026, 3, 5).toEpochDay(), 2_000_00), DayTotal(today.toEpochDay(), 20_000_00)),
                budget = null,
            )
        ).map { it.text }
        assertEquals(listOf("Biggest month: November, ₹20,000.00.", "Average ₹11,000.00 a month over 2 months with spending."), out)
    }
}
