package com.kyovo.cents.domain.port.output

interface UnitOfWork
{
    fun <T> execute(block: () -> T): T
}
