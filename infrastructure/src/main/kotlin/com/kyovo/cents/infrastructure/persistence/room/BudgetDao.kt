package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The SQL of the `budgets` table, checked against the schema at build time (KSP) like the others. */
@Dao
interface BudgetDao
{
    /**
     * Inserts the row, or updates it when its subcategory and month exist already. An update keeps the
     * row where it is, which "insert or replace" would not (it deletes then inserts).
     */
    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE subcategoryId = :subcategoryId")
    suspend fun deleteBySubcategoryId(subcategoryId: UUID)

    // rowid is the order the rows were first inserted in: the "stored order" of the repository.
    /** Emits the rows now, then again each time the table changes: Room watches it for us. */
    @Query("SELECT * FROM budgets ORDER BY rowid")
    fun observeAll(): Flow<List<BudgetEntity>>
}
