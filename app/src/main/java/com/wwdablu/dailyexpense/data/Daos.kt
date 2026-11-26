package com.wwdablu.dailyexpense.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wwdablu.dailyexpense.insights.DayTotal
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countWithName(name: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun expenseCount(categoryId: Long): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingEntry>>

    /** Returns the new row id, or -1 if this SMS was already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PendingEntry): Long

    @Query("DELETE FROM pending_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExpenseDao {
    @Query(
        """
        SELECT e.*, c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon
        FROM expenses e LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.epochDay = :epochDay
        ORDER BY e.createdAt DESC
        """
    )
    fun observeForDay(epochDay: Long): Flow<List<ExpenseWithCategory>>

    @Query(
        """
        SELECT e.*, c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon
        FROM expenses e LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.epochDay BETWEEN :fromDay AND :toDay
        ORDER BY e.epochDay, e.createdAt
        """
    )
    suspend fun entriesBetween(fromDay: Long, toDay: Long): List<ExpenseWithCategory>

    @Query(
        """
        SELECT e.categoryId AS categoryId, c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon,
               SUM(e.amountMinor) AS total
        FROM expenses e LEFT JOIN categories c ON e.categoryId = c.id
        WHERE e.epochDay BETWEEN :fromDay AND :toDay AND e.isIncome = :income
        GROUP BY e.categoryId
        ORDER BY total DESC
        """
    )
    fun observeTotalsByCategory(fromDay: Long, toDay: Long, income: Boolean): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN isIncome = 1 THEN amountMinor ELSE 0 END), 0) AS income,
               COALESCE(SUM(CASE WHEN isIncome = 0 THEN amountMinor ELSE 0 END), 0) AS expense
        FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay
        """
    )
    fun observePeriodTotals(fromDay: Long, toDay: Long): Flow<PeriodTotals>

    /** Total spent (expenses only) on each day that has any, for the insights. */
    @Query(
        """
        SELECT epochDay, SUM(amountMinor) AS total FROM expenses
        WHERE isIncome = 0 AND epochDay BETWEEN :fromDay AND :toDay
        GROUP BY epochDay
        """
    )
    fun observeDailySpending(fromDay: Long, toDay: Long): Flow<List<DayTotal>>

    @Insert
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}
