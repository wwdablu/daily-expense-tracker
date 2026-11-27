package com.wwdablu.dailyexpense.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Expense::class, PendingEntry::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun pendingDao(): PendingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Types added in v3. Bills already existed, so it is not repeated here. */
        private val ADDED_IN_V3 = listOf(
            "Baby" to 0xFFF06292,
            "Office" to 0xFF7986CB,
            "School" to 0xFFFFB74D,
            "Gifts" to 0xFFE57373,
            "Refunds" to 0xFF4DB6AC,
            "Grocery" to 0xFF81C784,
            "Education" to 0xFF9575CD,
            "Social" to 0xFF4FC3F7,
            "Telephone" to 0xFFA1887F,
        )

        /** Earning types added in v6. */
        private val ADDED_IN_V6 = listOf(
            "Lottery" to 0xFFFFCA28,
            "Sales" to 0xFF26A69A,
            "Interest" to 0xFF66BB6A,
            "Bonus" to 0xFF4FC3F7,
        )

        /** What each starter type is used for. Types not listed are expense types. */
        private val STARTER_KINDS = mapOf(
            "Salary" to TypeKind.Earning,
            "Gifts" to TypeKind.Both,
            "Refunds" to TypeKind.Refund,
            "Other" to TypeKind.Both,
        ) + ADDED_IN_V6.associate { it.first to TypeKind.Earning }

        private fun kindOf(name: String) = (STARTER_KINDS[name] ?: TypeKind.Expense).ordinal

        val DEFAULT_CATEGORIES = listOf(
            "Food" to 0xFFE57373,
            "Transport" to 0xFF64B5F6,
            "Shopping" to 0xFFBA68C8,
            "Bills" to 0xFFFFB74D,
            "Entertainment" to 0xFF4DB6AC,
            "Health" to 0xFF81C784,
            "Salary" to 0xFF4FC3F7,
            "Other" to 0xFF90A4AE,
        ) + ADDED_IN_V3 + ADDED_IN_V6

        /** v1 -> v2: entries can be an earning or an expense. Existing rows stay expenses. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN isIncome INTEGER NOT NULL DEFAULT 0")
                db.execSQL("INSERT OR IGNORE INTO categories (name, color) VALUES ('Salary', ${0xFF4FC3F7})")
            }
        }

        /** v2 -> v3: add more starter types, skipping any name the user already has (any case). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ADDED_IN_V3.forEach { (name, color) ->
                    db.execSQL(
                        "INSERT INTO categories (name, color) SELECT ?, ? " +
                            "WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ? COLLATE NOCASE)",
                        arrayOf<Any>(name, color, name),
                    )
                }
            }
        }

        /** v3 -> v4: each type can store the icon picked for it. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN icon TEXT")
            }
        }

        /** v4 -> v5: entries detected from bank SMS, waiting to be added or dismissed. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, `isIncome` INTEGER NOT NULL, `merchant` TEXT, " +
                        "`epochDay` INTEGER NOT NULL, `sender` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_entries_fingerprint` ON `pending_entries` (`fingerprint`)")
            }
        }

        /**
         * v5 -> v6: each type says whether it is for expenses, earnings, both, or refunds.
         * The user's own types get a kind from how they have been used. Refund entries
         * become negative expenses, and the new earning types are added.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
                val usedForIncome = "SELECT categoryId FROM expenses WHERE isIncome = 1 AND categoryId IS NOT NULL"
                val usedForExpense = "SELECT categoryId FROM expenses WHERE isIncome = 0 AND categoryId IS NOT NULL"
                db.execSQL("UPDATE categories SET kind = ${TypeKind.Both.ordinal} WHERE id IN ($usedForIncome) AND id IN ($usedForExpense)")
                db.execSQL("UPDATE categories SET kind = ${TypeKind.Earning.ordinal} WHERE id IN ($usedForIncome) AND id NOT IN ($usedForExpense)")
                STARTER_KINDS.forEach { (name, kind) ->
                    db.execSQL("UPDATE categories SET kind = ? WHERE name = ? COLLATE NOCASE", arrayOf<Any>(kind.ordinal, name))
                }
                db.execSQL(
                    "UPDATE expenses SET isIncome = 0, amountMinor = -ABS(amountMinor) " +
                        "WHERE categoryId IN (SELECT id FROM categories WHERE kind = ${TypeKind.Refund.ordinal})"
                )
                ADDED_IN_V6.forEach { (name, color) ->
                    db.execSQL(
                        "INSERT INTO categories (name, color, kind) SELECT ?, ?, ? " +
                            "WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = ? COLLATE NOCASE)",
                        arrayOf<Any>(name, color, TypeKind.Earning.ordinal, name),
                    )
                }
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seed a few starter types on first launch.
                        DEFAULT_CATEGORIES.forEach { (name, color) ->
                            db.execSQL(
                                "INSERT INTO categories (name, color, kind) VALUES (?, ?, ?)",
                                arrayOf<Any>(name, color, kindOf(name)),
                            )
                        }
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
