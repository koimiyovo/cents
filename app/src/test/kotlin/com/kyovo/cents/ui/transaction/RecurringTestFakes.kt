package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import java.util.UUID

/** Records the rules it is asked to create; can be told to refuse instead. */
internal class FakeCreateRecurring : CreateRecurringTransactionUseCase
{
    val commands = mutableListOf<CreateRecurringTransactionCommand>()
    var failWith: RuntimeException? = null

    override suspend fun create(command: CreateRecurringTransactionCommand): RecurringTransaction
    {
        failWith?.let { throw it }
        commands += command
        return command.toRecurringTransaction(RecurringTransactionId(UUID.randomUUID()))
    }
}

/** Counts the times it is asked to generate, and how many rules existed at each of them. */
internal class FakeGenerateRecurring(private val create: FakeCreateRecurring) : GenerateRecurringTransactionsUseCase
{
    val createdWhenCalled = mutableListOf<Int>()

    override suspend fun generate()
    {
        createdWhenCalled += create.commands.size
    }
}
