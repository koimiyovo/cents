package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The SQL of the `recurring_expenses` table, checked against the schema at build time (KSP) like the others. */
@Dao
interface RecurringExpenseDao
{
    /** Inserts the row, or updates it where it stands when its id exists already. */
    @Upsert
    suspend fun upsert(recurringExpense: RecurringExpenseEntity)

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun findById(id: UUID): RecurringExpenseEntity?

    // rowid is the order the rows were first inserted in: the "stored order" of the repository.
    @Query("SELECT * FROM recurring_expenses ORDER BY rowid")
    suspend fun findAll(): List<RecurringExpenseEntity>

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /** Emits the rows now, then again each time the table changes: Room watches it for us. */
    @Query("SELECT * FROM recurring_expenses ORDER BY rowid")
    fun observeAll(): Flow<List<RecurringExpenseEntity>>
}
