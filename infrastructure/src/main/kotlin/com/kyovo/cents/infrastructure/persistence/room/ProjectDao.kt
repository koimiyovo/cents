package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The SQL of the `projects` table. Room turns this interface into a class at build time (KSP),
 * checking every query against the schema: a typo in a column name is a build error, not a crash.
 */
@Dao
interface ProjectDao
{
    /**
     * Inserts the row, or updates it when its id exists already. An update keeps the row where it is,
     * which "insert or replace" would not (it deletes then inserts, so the row would jump to the end).
     */
    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun findById(id: UUID): ProjectEntity?

    // rowid is the order the rows were first inserted in: the "stored order" of the repository.
    @Query("SELECT * FROM projects ORDER BY rowid")
    suspend fun findAll(): List<ProjectEntity>

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: UUID)

    /** Emits the rows now, then again each time the table changes: Room watches it for us. */
    @Query("SELECT * FROM projects ORDER BY rowid")
    fun observeAll(): Flow<List<ProjectEntity>>

    /** Empties the table: what restoring a backup starts from. */
    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}
