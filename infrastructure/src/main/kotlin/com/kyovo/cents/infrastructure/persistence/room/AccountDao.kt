package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The SQL of the `accounts` table. It is an abstract class rather than an interface because two of its
 * functions are `@Transaction`s with a body: Room runs them in one database transaction, so they are
 * all-or-nothing and nobody can see them half done.
 */
@Dao
abstract class AccountDao
{
    @Upsert
    protected abstract suspend fun upsert(account: AccountEntity)

    @Query("SELECT position FROM accounts WHERE id = :id")
    protected abstract suspend fun positionOf(id: UUID): Int?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM accounts")
    protected abstract suspend fun nextPosition(): Int

    @Query("UPDATE accounts SET position = :position WHERE id = :id")
    protected abstract suspend fun setPosition(id: UUID, position: Int)

    /**
     * Saves the account where it already stands, or at the end if it is new: whatever position the
     * given row carries is ignored, since saving a renamed or archived account must not move it in a
     * list the user arranged by hand.
     */
    @Transaction
    open suspend fun save(account: AccountEntity)
    {
        val position = positionOf(account.id) ?: nextPosition()
        upsert(account.copy(position = position))
    }

    /**
     * The listed accounts take, in the given order, the positions the listed accounts already hold;
     * the others do not move. An id matching no account is ignored, so the rest still fit their slots.
     */
    @Transaction
    open suspend fun reorder(orderedIds: List<UUID>)
    {
        val stored = findAll()
        val known = orderedIds.filter { id -> stored.any { it.id == id } }
        val positions = stored.filter { it.id in known }.map { it.position }
        positions.zip(known).forEach { (position, id) -> setPosition(id, position) }
    }

    @Query("SELECT * FROM accounts WHERE id = :id")
    abstract suspend fun findById(id: UUID): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY position")
    abstract suspend fun findAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE archivedAt IS NULL")
    abstract suspend fun findActive(): List<AccountEntity>

    @Query("DELETE FROM accounts WHERE id = :id")
    abstract suspend fun deleteById(id: UUID)

    /** Emits the rows now, then again each time the table changes. */
    @Query("SELECT * FROM accounts ORDER BY position")
    abstract fun observeAll(): Flow<List<AccountEntity>>
}
