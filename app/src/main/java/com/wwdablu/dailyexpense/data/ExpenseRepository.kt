package com.wwdablu.dailyexpense.data

class ExpenseRepository(private val db: AppDatabase) {
    private val categories = db.categoryDao()
    private val expenses = db.expenseDao()

    fun observeCategories() = categories.observeAll()
    fun observeExpensesForDay(epochDay: Long) = expenses.observeForDay(epochDay)
    fun observeSpendingByCategory(fromDay: Long, toDay: Long) = expenses.observeTotalsByCategory(fromDay, toDay, income = false)
    fun observePeriodTotals(fromDay: Long, toDay: Long) = expenses.observePeriodTotals(fromDay, toDay)
    fun observeEarningsByCategory(fromDay: Long, toDay: Long) = expenses.observeTotalsByCategory(fromDay, toDay, income = true)
    fun observeDailySpending(fromDay: Long, toDay: Long) = expenses.observeDailySpending(fromDay, toDay)
    suspend fun entriesBetween(fromDay: Long, toDay: Long) = expenses.entriesBetween(fromDay, toDay)

    /** Returns an error message, or null on success. */
    suspend fun saveCategory(category: Category): String? {
        val name = category.name.trim()
        if (name.isEmpty()) return "Name can't be empty"
        if (categories.countWithName(name, category.id) > 0) return "\"$name\" already exists"
        val clean = category.copy(name = name)
        if (clean.id == 0L) categories.insert(clean) else categories.update(clean)
        return null
    }

    suspend fun expenseCountFor(category: Category) = categories.expenseCount(category.id)
    suspend fun deleteCategory(category: Category) = categories.delete(category)

    suspend fun saveExpense(expense: Expense) {
        if (expense.id == 0L) expenses.insert(expense) else expenses.update(expense)
    }

    suspend fun deleteExpense(expense: Expense) = expenses.delete(expense)
}
