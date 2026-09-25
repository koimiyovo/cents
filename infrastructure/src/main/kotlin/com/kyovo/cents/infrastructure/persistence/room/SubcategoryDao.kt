package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The SQL of the `subcategories` table. Room turns this interface into a class at build time (KSP),
 * checking every query against the schema: a typo in a column name is a build error, not a crash.
 */
@Dao
interface SubcategoryDao
{
    /**
     * Inserts the row, or updates it when its id exists already. An update keeps the row where it is,
     * which "insert or replace" would not (it deletes then inserts, so the row would jump to the end).
     */
    @Upsert
    suspend fun upsert(subcategory: SubcategoryEntity)

    @Query("SELECT * FROM subcategories WHERE id = :id")
    suspend fun findById(id: UUID): SubcategoryEntity?

    // rowid is the order the rows were first inserted in: the "stored order" of the repository.
    @Query("SELECT * FROM subcategories ORDER BY rowid")
    suspend fun findAll(): List<SubcategoryEntity>

    @Query("DELETE FROM subcategories WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /** Emits the rows now, then again each time the table changes: Room watches it for us. */
    @Query("SELECT * FROM subcategories ORDER BY rowid")
    fun observeAll(): Flow<List<SubcategoryEntity>>
}
