package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionCommand
import com.kyovo.cents.domain.port.output.RecurringTransactionIdGenerator
import com.kyovo.cents.domain.port.output.RecurringTransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.util.UUID

fun aRecurringTransactionId(value: String = "66666666-6666-6666-6666-666666666666"): RecurringTransactionId
{
    return RecurringTransactionId(UUID.fromString(value))
}

fun aRecurringTransaction(
    id: RecurringTransactionId = aRecurringTransactionId(),
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(80_000),
    title: TransactionTitle = aTransactionTitle("Loyer"),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    startDate: LocalDate = LocalDate.of(2026, 9, 5),
    endDate: LocalDate? = null,
    lastGeneratedDate: LocalDate? = null,
): RecurringTransaction
{
    return RecurringTransaction(
        id, accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate, lastGeneratedDate
    )
}

fun aCreateRecurringTransactionCommand(
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(80_000),
    title: TransactionTitle = aTransactionTitle("Loyer"),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    startDate: LocalDate = LocalDate.of(2026, 9, 5),
    endDate: LocalDate? = null,
): CreateRecurringTransactionCommand
{
    return CreateRecurringTransactionCommand(
        accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate
    )
}

fun anUpdateRecurringTransactionCommand(
    id: RecurringTransactionId = aRecurringTransactionId(),
    amount: Money = aMoney(80_000),
    title: TransactionTitle = aTransactionTitle("Loyer"),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    endDate: LocalDate? = null,
): UpdateRecurringTransactionCommand
{
    return UpdateRecurringTransactionCommand(id, amount, title, subcategoryId, description, frequency, interval, endDate)
}

class FixedRecurringTransactionIdGenerator(private val id: RecurringTransactionId) : RecurringTransactionIdGenerator
{
    override fun generate(): RecurringTransactionId = id
}

class InMemoryRecurringTransactionRepository : RecurringTransactionRepository
{
    private val state = MutableStateFlow<List<RecurringTransaction>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<RecurringTransaction> get() = state.value

    override suspend fun save(recurringTransaction: RecurringTransaction)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == recurringTransaction.id }
        state.value =
            if (index >= 0) current.toMutableList().also { it[index] = recurringTransaction } else current + recurringTransaction
    }

    override suspend fun findById(id: RecurringTransactionId): RecurringTransaction?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun findAll(): List<RecurringTransaction>
    {
        return state.value
    }

    override suspend fun deleteById(id: RecurringTransactionId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<RecurringTransaction>>
    {
        return state
    }
}
