package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.port.output.UnitOfWork

class InMemoryUnitOfWork : UnitOfWork
{
    var executionCount = 0
        private set

    override suspend fun <T> execute(block: suspend () -> T): T
    {
        executionCount++
        return block()
    }
}
