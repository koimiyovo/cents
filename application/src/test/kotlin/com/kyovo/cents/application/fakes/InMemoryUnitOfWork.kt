package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.port.output.UnitOfWork

class InMemoryUnitOfWork : UnitOfWork
{
    var executionCount = 0
        private set

    override fun <T> execute(block: () -> T): T
    {
        executionCount++
        return block()
    }
}
