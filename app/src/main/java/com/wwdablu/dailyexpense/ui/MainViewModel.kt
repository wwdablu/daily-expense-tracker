package com.wwdablu.dailyexpense.ui

import android.app.Application
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.wwdablu.dailyexpense.data.BudgetStore
import com.wwdablu.dailyexpense.data.PeriodTotals
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

    /** Spending per expense type for the month containing the selected day. */
    val monthTotals: StateFlow<List<CategoryTotal>> = _selectedDay
        .map { monthRange(it) }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) -> repo.observeSpendingByCategory(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Earned / spent totals for the month containing the selected day. */
    val monthSums: StateFlow<PeriodTotals> = _selectedDay
        .map { monthRange(it) }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) -> repo.observePeriodTotals(from, to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeriodTotals(0, 0))

    private val budgetStore = BudgetStore(app)
    /** Maximum monthly budget in paise, or null if not set. */
    val budget: StateFlow<Long?> = budgetStore.budget
    fun setBudget(minor: Long?) = budgetStore.set(minor)

    fun selectDay(day: LocalDate) { _selectedDay.value = day }
    fun shiftDay(days: Long) { _selectedDay.value = _selectedDay.value.plusDays(days) }
    fun shiftMonth(months: Long) { _selectedDay.value = _selectedDay.value.plusMonths(months) }

    fun saveExpense(expense: Expense) = viewModelScope.launch { repo.saveExpense(expense) }
    fun deleteExpense(expense: Expense) = viewModelScope.launch { repo.deleteExpense(expense) }

    fun saveCategory(category: Category, onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(repo.saveCategory(category))
    }

    fun expenseCountFor(category: Category, onResult: (Int) -> Unit) = viewModelScope.launch {
        onResult(repo.expenseCountFor(category))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch { repo.deleteCategory(category) }
}
