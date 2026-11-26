package com.wwdablu.dailyexpense.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A type (category) of expense, e.g. Food, Transport. */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color used behind the type's icon. */
    val color: Long,
    /** Key of the chosen icon (see TYPE_ICONS). null = pick one from the name. */
    val icon: String? = null,
)

/**
 * A single expense. [amountMinor] is stored in minor units (cents/paise) to avoid
 * floating-point rounding errors. [epochDay] is LocalDate.toEpochDay().
 * If the category is removed, existing expenses are kept and become "Uncategorized".
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("categoryId"), Index("epochDay")]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val categoryId: Long?,
    val note: String,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** true = money earned (income), false = money spent (expense). */
    @ColumnInfo(defaultValue = "0") val isIncome: Boolean = false,
)

/**
 * A debit or credit detected in a bank SMS, waiting for the user to add or dismiss it.
 * Only the parsed details are kept, not the message text. [fingerprint] stops the same SMS
 * from being suggested twice.
 */
@Entity(tableName = "pending_entries", indices = [Index(value = ["fingerprint"], unique = true)])
data class PendingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val isIncome: Boolean,
    val merchant: String?,
    val epochDay: Long,
    /** SMS sender ID, e.g. "VM-HDFCBK". */
    val sender: String,
    val fingerprint: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Earned and spent totals for a date range, in minor units (paise). */
data class PeriodTotals(val income: Long, val expense: Long)

data class ExpenseWithCategory(
    @Embedded val expense: Expense,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "categoryColor") val categoryColor: Long?,
    @ColumnInfo(name = "categoryIcon") val categoryIcon: String?,
)

data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String?,
    val categoryColor: Long?,
    val categoryIcon: String?,
    val total: Long,
)
