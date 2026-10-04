package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetAlert
import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.infrastructure.persistence.assertThatThrownBySuspending
import com.kyovo.cents.infrastructure.persistence.realTime
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Version 1 of the database is installed on a real phone, holding real accounts. Opening that file with
 * the app of version 2 must add the budgets and lose nothing: the migration is the only thing standing
 * between the user and an empty app, and the contract tests never exercise it (they start from a new
 * version 2 file).
 *
 * Room 3's `MigrationTestHelper` cannot run in these host tests, so this is written by hand: it builds a
 * **version 1 file with plain SQL**, then opens it with the real database class and the real migrations.
 * The SQL below is a frozen copy of `schemas/.../1.json` — version 1 never changes (`SchemaGuardTest` pins
 * its hash), so it does not go stale, and it does not depend on the entities of today, which are already
 * version 2. Opening also makes Room compare the migrated tables with the schema it expects, and refuse
 * the file if they differ: that check is part of what these tests exercise.
 */
class CentsMigrationsTest
{
    @TempDir
    lateinit var folder: File

    private val path get() = File(folder, "cents.db").absolutePath

    private val groceriesId = UUID.fromString("55555555-5555-5555-5555-555555555551")
    private val accountId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val expenseId = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val groceries = Subcategory(
        SubcategoryId(groceriesId),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        Emoji("🛒"),
    )
    private val september = YearMonth.of(2026, 9)

    /** A UUID as a SQL blob literal, the way Room stores it: 16 bytes. */
    private fun blob(id: UUID) = "x'${id.toString().replace("-", "")}'"

    /**
     * A version 1 file as the released app leaves it: the tables and indices of `1.json`, Room's own
     * bookkeeping (the identity hash of version 1 and `user_version` = 1), and some real-looking data — a
     * subcategory, an account, and an expense of that account in that subcategory.
     */
    private fun createVersion1File()
    {
        val at = Instant.parse("2026-09-01T10:00:00Z").toEpochNanos()
        val connection = BundledSQLiteDriver().open(path)
        try
        {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `subcategories` (`id` BLOB NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT, PRIMARY KEY(`id`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archivedAt` INTEGER, `description` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_position` ON `accounts` (`position`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` BLOB NOT NULL, `accountId` BLOB NOT NULL, `amount` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `subcategoryId` BLOB, `description` TEXT, `date` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_subcategoryId` ON `transactions` (`subcategoryId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6443675907e21e5be77fbe107eff03cc')")
            connection.execSQL("PRAGMA user_version = 1")

            connection.execSQL(
                "INSERT INTO subcategories (id, kind, name, emoji) VALUES (${
                    blob(
                        groceriesId
                    )
                }, 'EXPENSE', 'Alimentation', '🛒')"
            )
            connection.execSQL(
                "INSERT INTO accounts (id, name, type, currency, createdAt, archivedAt, description, position) VALUES (${
                    blob(
                        accountId
                    )
                }, 'Compte courant', 'CHECKING', 'EUR', $at, NULL, NULL, 0)"
            )
            connection.execSQL(
                "INSERT INTO transactions (id, accountId, amount, title, category, subcategoryId, description, date) VALUES (${
                    blob(
                        expenseId
                    )
                }, ${blob(accountId)}, 1250, 'Courses', 'EXPENSE', ${blob(groceriesId)}, NULL, $at)"
            )
        } finally
        {
            connection.close()
        }
    }


    private val rentRuleId = UUID.fromString("77777777-7777-7777-7777-777777777771")

    /**
     * A version 4 file as the released app leaves it: the tables and indices of `4.json` (a frozen copy —
     * version 4 is installed on a phone, so it never changes), Room's bookkeeping (its identity hash and
     * `user_version` = 4), and some real-looking data: a subcategory, an account, and a recurring **expense**
     * (a rent) in the table that was still called `recurring_expenses`.
     */
    private fun createVersion4File()
    {
        val at = Instant.parse("2026-09-01T10:00:00Z").toEpochNanos()
        val connection = BundledSQLiteDriver().open(path)
        try
        {
            connection.execSQL("CREATE TABLE IF NOT EXISTS `subcategories` (`id` BLOB NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT, PRIMARY KEY(`id`))")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `currency` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archivedAt` INTEGER, `description` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_position` ON `accounts` (`position`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` BLOB NOT NULL, `accountId` BLOB NOT NULL, `amount` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `subcategoryId` BLOB, `description` TEXT, `date` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_subcategoryId` ON `transactions` (`subcategoryId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`subcategoryId` BLOB NOT NULL, `month` INTEGER NOT NULL, `limitCents` INTEGER NOT NULL, `alertPercent` INTEGER NOT NULL, PRIMARY KEY(`subcategoryId`, `month`), FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `recurring_expenses` (`id` BLOB NOT NULL, `accountId` BLOB NOT NULL, `amount` INTEGER NOT NULL, `title` TEXT NOT NULL, `subcategoryId` BLOB, `description` TEXT, `frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `lastGeneratedDate` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_accountId` ON `recurring_expenses` (`accountId`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_expenses_subcategoryId` ON `recurring_expenses` (`subcategoryId`)")
            connection.execSQL("CREATE TABLE IF NOT EXISTS `budget_alerts` (`subcategoryId` BLOB NOT NULL, `month` INTEGER NOT NULL, `level` TEXT NOT NULL, PRIMARY KEY(`subcategoryId`, `month`, `level`), FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6579189556ef3eb2ac146eeb26bbe69e')")
            connection.execSQL("PRAGMA user_version = 4")

            connection.execSQL(
                "INSERT INTO subcategories (id, kind, name, emoji) VALUES (${blob(groceriesId)}, 'EXPENSE', 'Alimentation', '🛒')"
            )
            connection.execSQL(
                "INSERT INTO accounts (id, name, type, currency, createdAt, archivedAt, description, position) VALUES (${blob(accountId)}, 'Compte courant', 'CHECKING', 'EUR', $at, NULL, NULL, 0)"
            )
            connection.execSQL(
                "INSERT INTO recurring_expenses (id, accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate, lastGeneratedDate) VALUES (${blob(rentRuleId)}, ${blob(accountId)}, 80000, 'Loyer', ${blob(groceriesId)}, NULL, 'MONTHLY', 1, ${LocalDate.of(2026, 9, 5).toEpochDay()}, NULL, ${LocalDate.of(2026, 12, 5).toEpochDay()})"
            )
        } finally
        {
            connection.close()
        }
    }

    /** The names of the tables (or indices) of the file, as SQLite lists them. */
    private fun namesInTheFile(type: String): List<String>
    {
        val connection = BundledSQLiteDriver().open(path)
        try
        {
            return connection.prepare("SELECT name FROM sqlite_master WHERE type = '$type'").use { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        } finally
        {
            connection.close()
        }
    }

    /** Opens the file with the real database class and the real migrations, like the app does. */
    private fun openMigrated(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*CentsMigrations.ALL.toTypedArray())
            .build()

    private fun userVersionOfTheFile(): Int
    {
        val connection = BundledSQLiteDriver().open(path)
        try
        {
            return connection.prepare("PRAGMA user_version").use { statement ->
                statement.step()
                statement.getLong(0).toInt()
            }
        } finally
        {
            connection.close()
        }
    }

    @Test
    fun `opening a version 1 file keeps its subcategories, accounts and transactions`() = realTime()
    {
        // GIVEN the file the released app left on the phone
        createVersion1File()

        // WHEN
        val database = openMigrated()
        try
        {
            // THEN nothing was lost, and it reads back through the current adapters
            assertThat(RoomSubcategoryRepository(database.subcategoryDao()).observeAll().first())
                .containsExactly(groceries)

            val accounts = RoomAccountRepository(database.accountDao()).observeAll().first()
            assertThat(accounts.map { it.name.value }).containsExactly("Compte courant")
            assertThat(accounts.single().type).isEqualTo(AccountType.CHECKING)

            val transactions =
                RoomTransactionRepository(database.transactionDao()).observeAll().first()
            val expense = transactions.single()
            assertThat(expense.category).isEqualTo(TransactionCategory.EXPENSE)
            assertThat(expense.amount).isEqualTo(Money(1_250))
            assertThat(expense.subcategoryId).isEqualTo(groceries.id)
        } finally
        {
            database.close()
        }
    }

    @Test
    fun `the migrated database has a budgets table that starts empty and works`() = realTime()
    {
        // GIVEN
        createVersion1File()
        val database = openMigrated()
        try
        {
            val budgets = RoomBudgetRepository(database.budgetDao())

            // WHEN / THEN nobody had a budget before, and one can now be set on an existing subcategory
            assertThat(budgets.observeAll().first()).isEmpty()

            val budget = Budget(groceries.id, september, Money(30_000))
            budgets.save(budget)
            budgets.save(budget.copy(limit = Money(45_000)))

            assertThat(budgets.observeAll().first()).containsExactly(
                budget.copy(
                    limit = Money(
                        45_000
                    )
                )
            )
        } finally
        {
            database.close()
        }
    }

    // The foreign key is part of the migrated table: without it, deleting a subcategory would leave
    // budgets pointing at nothing, in a migrated database only — a difference no new database shows.
    @Test
    fun `a budget of the migrated database is refused for an unknown subcategory and goes with its own`() =
        realTime()
        {
            // GIVEN
            createVersion1File()
            val database = openMigrated()
            try
            {
                val budgets = RoomBudgetRepository(database.budgetDao())
                val subcategories = RoomSubcategoryRepository(database.subcategoryDao())
                val unknown = SubcategoryId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

                // WHEN / THEN it cannot point to a subcategory that does not exist
                assertThatThrownBySuspending {
                    budgets.save(
                        Budget(
                            unknown,
                            september,
                            Money(30_000)
                        )
                    )
                }.isNotNull()
                assertThat(budgets.observeAll().first()).isEmpty()

                // AND deleting the subcategory takes its budgets with it, while its transaction stays
                budgets.save(Budget(groceries.id, september, Money(30_000)))
                subcategories.deleteById(groceries.id)

                assertThat(budgets.observeAll().first()).isEmpty()
                val transactions =
                    RoomTransactionRepository(database.transactionDao()).observeAll().first()
                assertThat(transactions.single().subcategoryId).isNull()
            } finally
            {
                database.close()
            }
        }

    @Test
    fun `a migrated file is at version 5, and what is saved afterwards, threshold included, survives reopening it`() =
        realTime()
        {
            // GIVEN a version 1 file, migrated, with a budget set
            createVersion1File()
            val budget = Budget(groceries.id, september, Money(30_000), AlertThreshold(60))
            val first = openMigrated()
            try
            {
                RoomBudgetRepository(first.budgetDao()).save(budget)
            } finally
            {
                first.close()
            }
            assertThat(userVersionOfTheFile()).isEqualTo(5)

            // WHEN it is opened again (the migrations must not run a second time, nor fail)
            val second = openMigrated()
            try
            {
                // THEN
                assertThat(
                    RoomBudgetRepository(second.budgetDao()).observeAll().first()
                ).containsExactly(budget)
                assertThat(
                    RoomSubcategoryRepository(second.subcategoryDao()).observeAll().first()
                ).containsExactly(groceries)
            } finally
            {
                second.close()
            }
        }

    @Test
    fun `the migrated database has a recurring_transactions table that starts empty and works`() = realTime()
    {
        // GIVEN
        createVersion1File()
        val database = openMigrated()
        try
        {
            val recurringTransactions = RoomRecurringTransactionRepository(database.recurringTransactionDao())

            // WHEN / THEN nobody had a rule before, and one can now be set on an existing subcategory
            assertThat(recurringTransactions.observeAll().first()).isEmpty()

            val ruleId = RecurringTransactionId(UUID.fromString("77777777-7777-7777-7777-777777777777"))
            val rule = RecurringTransaction(
                ruleId, AccountId(accountId), RecordableTransactionCategory.EXPENSE, Money(80_000), TransactionTitle("Loyer"), groceries.id, null,
                RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 9, 5),
            )
            recurringTransactions.save(rule)
            recurringTransactions.save(rule.copy(amount = Money(85_000)))

            assertThat(recurringTransactions.observeAll().first()).containsExactly(rule.copy(amount = Money(85_000)))
        } finally
        {
            database.close()
        }
    }

    // The foreign key is part of the migrated table: without it, deleting a subcategory would leave a
    // recurring expense pointing at nothing, in a migrated database only — a difference no new database shows.
    @Test
    fun `a recurring expense of the migrated database loses its subcategory when that subcategory is deleted`() =
        realTime()
        {
            // GIVEN
            createVersion1File()
            val database = openMigrated()
            try
            {
                val recurringTransactions = RoomRecurringTransactionRepository(database.recurringTransactionDao())
                val subcategories = RoomSubcategoryRepository(database.subcategoryDao())
                val ruleId = RecurringTransactionId(UUID.fromString("77777777-7777-7777-7777-777777777777"))
                val rule = RecurringTransaction(
                    ruleId, AccountId(accountId), RecordableTransactionCategory.EXPENSE, Money(80_000), TransactionTitle("Loyer"), groceries.id, null,
                    RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 9, 5),
                )
                recurringTransactions.save(rule)

                // WHEN
                subcategories.deleteById(groceries.id)

                // THEN
                assertThat(recurringTransactions.findById(ruleId)?.subcategoryId).isNull()
            } finally
            {
                database.close()
            }
        }

    @Test
    fun `the migrated database has a budget_alerts table that starts empty and works`() = realTime()
    {
        // GIVEN
        createVersion1File()
        val database = openMigrated()
        try
        {
            val budgetAlerts = RoomBudgetAlertRepository(database.budgetAlertDao())

            // WHEN / THEN nothing was reported before, and a crossing can now be recorded on an existing subcategory
            assertThat(budgetAlerts.findByMonth(september)).isEmpty()

            val alert = BudgetAlert(groceries.id, september, BudgetAlertLevel.CLOSE_TO_LIMIT)
            budgetAlerts.record(alert)
            budgetAlerts.record(alert)

            assertThat(budgetAlerts.findByMonth(september)).containsExactly(alert)
        } finally
        {
            database.close()
        }
    }

    // The foreign key is part of the migrated table: without it, deleting a subcategory would leave a
    // budget alert pointing at nothing, in a migrated database only — a difference no new database shows.
    @Test
    fun `a budget alert of the migrated database is deleted along with its subcategory`() = realTime()
    {
        // GIVEN
        createVersion1File()
        val database = openMigrated()
        try
        {
            val budgetAlerts = RoomBudgetAlertRepository(database.budgetAlertDao())
            val subcategories = RoomSubcategoryRepository(database.subcategoryDao())
            budgetAlerts.record(BudgetAlert(groceries.id, september, BudgetAlertLevel.OVER))

            // WHEN
            subcategories.deleteById(groceries.id)

            // THEN
            assertThat(budgetAlerts.findByMonth(september)).isEmpty()
        } finally
        {
            database.close()
        }
    }

    // ------------------------------------------------------------------ version 4 -> 5 (recurring incomes)

    @Test
    fun `a version 4 file keeps its recurring rule, now an expense in the renamed table`() = realTime()
    {
        // GIVEN a phone whose app of version 4 had one rule, a rent
        createVersion4File()

        // WHEN the app of version 5 opens it
        val database = openMigrated()
        try
        {
            val rules = RoomRecurringTransactionRepository(database.recurringTransactionDao()).findAll()

            // THEN nothing was lost, and what could only be an expense before still is one
            assertThat(rules).hasSize(1)
            val rent = rules.single()
            assertThat(rent.id).isEqualTo(RecurringTransactionId(rentRuleId))
            assertThat(rent.category).isEqualTo(RecordableTransactionCategory.EXPENSE)
            assertThat(rent.accountId).isEqualTo(AccountId(accountId))
            assertThat(rent.amount).isEqualTo(Money(80_000))
            assertThat(rent.title).isEqualTo(TransactionTitle("Loyer"))
            assertThat(rent.subcategoryId).isEqualTo(groceries.id)
            assertThat(rent.frequency).isEqualTo(RecurrenceFrequency.MONTHLY)
            assertThat(rent.interval).isEqualTo(1)
            assertThat(rent.startDate).isEqualTo(LocalDate.of(2026, 9, 5))
            assertThat(rent.endDate).isNull()
            assertThat(rent.lastGeneratedDate).isEqualTo(LocalDate.of(2026, 12, 5))
            assertThat(userVersionOfTheFile()).isEqualTo(5)
        } finally
        {
            database.close()
        }
    }

    @Test
    fun `the migrated file has the recurring_transactions table and its indices, and no recurring_expenses any more`() =
        realTime()
        {
            // GIVEN a version 4 file, opened and read once (Room only opens, and so migrates, the file on first use)
            createVersion4File()
            val database = openMigrated()
            try
            {
                RoomRecurringTransactionRepository(database.recurringTransactionDao()).findAll()
            } finally
            {
                database.close()
            }

            // WHEN / THEN
            assertThat(namesInTheFile("table")).contains("recurring_transactions").doesNotContain("recurring_expenses")
            assertThat(namesInTheFile("index"))
                .contains("index_recurring_transactions_accountId", "index_recurring_transactions_subcategoryId")
                .doesNotContain("index_recurring_expenses_accountId", "index_recurring_expenses_subcategoryId")
        }

    @Test
    fun `a recurring income can be saved in a migrated file, next to the old expense, and survives reopening it`() =
        realTime()
        {
            // GIVEN a version 4 file, migrated, with a salary added
            createVersion4File()
            val salary = RecurringTransaction(
                RecurringTransactionId(UUID.fromString("77777777-7777-7777-7777-777777777772")),
                AccountId(accountId), RecordableTransactionCategory.INCOME, Money(200_000),
                TransactionTitle("Salaire"), null, null, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 9, 28),
            )
            val first = openMigrated()
            try
            {
                RoomRecurringTransactionRepository(first.recurringTransactionDao()).save(salary)
            } finally
            {
                first.close()
            }

            // WHEN it is opened again (the migration must not run a second time, nor fail)
            val second = openMigrated()
            try
            {
                // THEN the rent is still an expense and the salary is still an income
                val rules = RoomRecurringTransactionRepository(second.recurringTransactionDao()).findAll()
                assertThat(rules.map { it.title.value to it.category }).containsExactly(
                    "Loyer" to RecordableTransactionCategory.EXPENSE,
                    "Salaire" to RecordableTransactionCategory.INCOME,
                )
            } finally
            {
                second.close()
            }
        }
}
