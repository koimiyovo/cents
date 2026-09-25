package com.kyovo.cents.domain.port.output

interface UnitOfWork
{
    suspend fun <T> execute(block: suspend () -> T): T
}
