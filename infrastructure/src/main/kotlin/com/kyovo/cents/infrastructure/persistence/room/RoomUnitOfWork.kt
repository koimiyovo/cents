package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.withWriteTransaction
import com.kyovo.cents.domain.port.output.UnitOfWork

/**
 * Makes the writes of a block all-or-nothing with a real database transaction: if the block completes
 * they are all kept, if it throws they are all undone and the exception goes on to the caller.
 *
 * The repositories used inside the block need nothing special: Room notices that the coroutine is
 * inside a transaction and runs their queries on that same connection, so the block also reads what
 * it has just written. And nobody else sees the writes before the end.
 */
class RoomUnitOfWork(private val database: CentsDatabase) : UnitOfWork
{
    override suspend fun <T> execute(block: suspend () -> T): T
    {
        return database.withWriteTransaction { block() }
    }
}
