package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.withWriteTransaction
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.port.output.BackupRestorer

/**
 * Puts a snapshot in place of everything in the database, in one transaction: if anything fails (a
 * foreign key the snapshot breaks, a full disk) everything is undone and the old data is untouched.
 *
 * Everything is deleted first, children before the parents their foreign keys point to (an account with
 * transactions cannot go), then written back parents first. Accounts are saved one by one, in the
 * snapshot's order, which is what gives them their positions. The budget alerts already reported are
 * not restored, only forgotten: they are derived, and the next check recomputes them.
 */
class RoomBackupRestorer(private val database: CentsDatabase) : BackupRestorer
{
    override suspend fun replaceAll(snapshot: BackupSnapshot)
    {
        database.withWriteTransaction {
            database.transactionDao().deleteAll()
            database.recurringTransactionDao().deleteAll()
            database.budgetDao().deleteAll()
            database.budgetAlertDao().deleteAll()
            database.accountDao().deleteAll()
            database.subcategoryDao().deleteAll()
            database.projectDao().deleteAll()
            database.budgetCalendarDao().deleteAllStarts()
            database.budgetCalendarDao().deleteSettings()

            snapshot.projects.forEach { database.projectDao().upsert(it.toEntity()) }
            snapshot.subcategories.forEach { database.subcategoryDao().upsert(it.toEntity()) }
            snapshot.accounts.forEach { database.accountDao().save(it.toEntity(position = 0)) }
            snapshot.transactions.forEach { database.transactionDao().upsert(it.toEntity()) }
            snapshot.recurringTransactions.forEach { database.recurringTransactionDao().upsert(it.toEntity()) }
            snapshot.budgets.forEach { database.budgetDao().upsert(it.toEntity()) }

            val calendar = snapshot.budgetCalendar
            database.budgetCalendarDao().upsertSettings(BudgetSettingsEntity(defaultStartDay = calendar.defaultStartDay.value))
            calendar.declaredStarts.forEach { database.budgetCalendarDao().upsertStart(it.toCycleStartEntity()) }
        }
    }
}
