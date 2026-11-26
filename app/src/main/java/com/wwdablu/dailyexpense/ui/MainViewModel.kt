package com.wwdablu.dailyexpense.ui

import android.app.Application
import android.net.Uri
import com.wwdablu.dailyexpense.R
import com.wwdablu.dailyexpense.export.CsvWriter
import com.wwdablu.dailyexpense.export.PdfWriter
import com.wwdablu.dailyexpense.export.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwdablu.dailyexpense.data.AppDatabase
import com.wwdablu.dailyexpense.data.Category
import com.wwdablu.dailyexpense.data.CategoryTotal
import com.wwdablu.dailyexpense.data.Expense
import com.wwdablu.dailyexpense.data.ExpenseRepository
import com.wwdablu.dailyexpense.data.ExpenseWithCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.wwdablu.dailyexpense.data.AppSettings
import com.wwdablu.dailyexpense.reminder.DailyReminder
import com.wwdablu.dailyexpense.widget.TodayWidget
import com.wwdablu.dailyexpense.data.PeriodTotals
import com.wwdablu.dailyexpense.data.PendingEntry
import com.wwdablu.dailyexpense.sms.TypeGuess
import com.wwdablu.dailyexpense.insights.Insight
import com.wwdablu.dailyexpense.insights.InsightInputs
import com.wwdablu.dailyexpense.insights.InsightRanges
import com.wwdablu.dailyexpense.insights.InsightsCalculator
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ExpenseRepository(AppDatabase.get(app))

    private val _selectedDay = MutableStateFlow(LocalDate.now())
    val selectedDay: StateFlow<LocalDate> = _selectedDay.asStateFlow()

    val categories: StateFlow<List<Category>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayExpenses: StateFlow<List<ExpenseWithCategory>> = _selectedDay
        .flatMapLatest { repo.observeExpensesForDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun monthRange(day: LocalDate) =
        day.withDayOfMonth(1).toEpochDay() to day.withDayOfMonth(day.lengthOfMonth()).toEpochDay()

    private fun yearRange(day: LocalDate) =
        day.withDayOfYear(1).toEpochDay() to day.withDayOfYear(day.lengthOfYear()).toEpochDay()

    /** Whether the Summary tab shows the selected day's month or its whole year. */
    private val _summaryPeriod = MutableStateFlow(SummaryPeriod.Month)
    val summaryPeriod: StateFlow<SummaryPeriod> = _summaryPeriod.asStateFlow()
    fun setSummaryPeriod(period: SummaryPeriod) { _summaryPeriod.value = period }

    private val summaryRange = combine(_selectedDay, _summaryPeriod) { day, period ->
        if (period == SummaryPeriod.Month) monthRange(day) else yearRange(day)
    }.distinctUntilChanged()

    /** Spending per expense type for the Summary tab's month or year. */
    val summaryTotals: StateFlow<List<CategoryTotal>> = summaryRange
        .flatMapLatest { (from, to) -> repo.observeSpendingByCategory(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Earned / spent totals for the Summary tab's month or year. */
    val summarySums: StateFlow<PeriodTotals> = summaryRange
        .flatMapLatest { (from, to) -> repo.observePeriodTotals(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodTotals(0, 0))

    /** Insights for the Summary tab's month or year, recomputed whenever entries change. */
    val insights: StateFlow<List<Insight>> = combine(_selectedDay, _summaryPeriod, AppSettings.get(app).budget) { day, period, budget ->
        Triple(InsightRanges.of(day, period == SummaryPeriod.Year, LocalDate.now()), budget, Unit)
    }.distinctUntilChanged().flatMapLatest { (r, budget, _) ->
        val from = r.start.toEpochDay(); val to = r.end.toEpochDay()
        val pFrom = r.prevStart.toEpochDay(); val pTo = r.prevEnd.toEpochDay()
        combine(
            repo.observePeriodTotals(from, to),
            repo.observePeriodTotals(pFrom, pTo),
            repo.observeSpendingByCategory(from, to),
            repo.observeSpendingByCategory(pFrom, pTo),
            repo.observeDailySpending(from, to),
        ) { cur, prev, byType, prevByType, daily ->
            InsightsCalculator.compute(InsightInputs(r, cur.expense, prev.expense, byType, prevByType, daily, budget))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Earned / spent totals for the month containing the selected day (drives the budget). */
    val monthSums: StateFlow<PeriodTotals> = _selectedDay
        .map { monthRange(it) }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) -> repo.observePeriodTotals(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodTotals(0, 0))

    private val settings = AppSettings.get(app)
    /** Maximum monthly budget in paise, or null if not set. */
    val budget: StateFlow<Long?> = settings.budget
    fun setBudget(minor: Long?) {
        settings.setBudget(minor)
        TodayWidget.refresh(getApplication())
    }

    /** Debits/credits found in bank SMS, waiting to be added or dismissed. */
    private val pendingDao = AppDatabase.get(app).pendingDao()
    val pending: StateFlow<List<PendingEntry>> = pendingDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun dismissPending(id: Long) = viewModelScope.launch { pendingDao.delete(id) }

    /** A new entry pre-filled from a detected SMS, with the type guessed from the merchant. */
    fun draftFrom(p: PendingEntry): Expense {
        val guess = TypeGuess.typeName(p.merchant.orEmpty())
        val category = categories.value.firstOrNull { it.name.equals(guess, ignoreCase = true) }
        return Expense(
            amountMinor = p.amountMinor,
            categoryId = category?.id,
            note = p.merchant.orEmpty(),
            epochDay = p.epochDay,
            isIncome = p.isIncome,
        )
    }

    val smsDetection: StateFlow<Boolean> = settings.smsDetection
    fun setSmsDetection(on: Boolean) = settings.setSmsDetection(on)

    val reminderEnabled: StateFlow<Boolean> = settings.reminderEnabled
    val reminderMinutes: StateFlow<Int> = settings.reminderMinutes
    fun setReminderEnabled(on: Boolean) {
        settings.setReminderEnabled(on)
        DailyReminder.schedule(getApplication())
    }
    fun setReminderMinutes(minutes: Int) {
        settings.setReminderMinutes(minutes)
        DailyReminder.schedule(getApplication())
    }

    fun selectDay(day: LocalDate) { _selectedDay.value = day }
    fun shiftDay(days: Long) { _selectedDay.value = _selectedDay.value.plusDays(days) }
    fun shiftMonth(months: Long) { _selectedDay.value = _selectedDay.value.plusMonths(months) }
    fun shiftYear(years: Long) { _selectedDay.value = _selectedDay.value.plusYears(years) }

    private val appName = app.getString(R.string.app_name)

    fun exportFileName(isYear: Boolean, csv: Boolean = false) =
        Report.fileName(appName, _selectedDay.value, isYear, if (csv) "csv" else "pdf")

    /**
     * Writes the month or year containing the selected day to [uri] as a PDF, or as CSV if [csv].
     * [onDone] gets null on success or an error message.
     */
    fun export(isYear: Boolean, uri: Uri, csv: Boolean = false, onDone: (String?) -> Unit) = viewModelScope.launch {
        val error = runCatching {
            withContext(Dispatchers.IO) {
                val report = Report.build(repo, appName, _selectedDay.value, isYear, budget.value)
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri, "wt")!!.use { out ->
                    if (csv) CsvWriter.write(report, out) else PdfWriter.write(report, out)
                }
            }
        }.exceptionOrNull()
        onDone(error?.let { it.message ?: it.javaClass.simpleName })
    }

    fun saveExpense(expense: Expense) = viewModelScope.launch {
        repo.saveExpense(expense)
        TodayWidget.refresh(getApplication())
    }
    fun deleteExpense(expense: Expense) = viewModelScope.launch {
        repo.deleteExpense(expense)
        TodayWidget.refresh(getApplication())
    }

    fun saveCategory(category: Category, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(repo.saveCategory(category))
    }

    fun expenseCountFor(category: Category, onResult: (Int) -> Unit) = viewModelScope.launch {
        onResult(repo.expenseCountFor(category))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch { repo.deleteCategory(category) }
}

enum class SummaryPeriod { Month, Year }
