package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The SQL of the `transactions` table. */
@Dao
interface TransactionDao
{
    /** Inserts the row, or updates it where it stands when its id exists already. */
    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: UUID): TransactionEntity?

    // rowid is the order the rows were first inserted in: the "stored order" of the repository.
    @Query("SELECT * FROM transactions ORDER BY rowid")
    suspend fun findAll(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /** Emits the rows now, then again each time the table changes. */
    @Query("SELECT * FROM transactions ORDER BY rowid")
    fun observeAll(): Flow<List<TransactionEntity>>
}
