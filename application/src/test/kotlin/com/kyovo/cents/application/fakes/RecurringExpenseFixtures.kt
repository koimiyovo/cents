package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseCommand
import com.kyovo.cents.domain.port.output.RecurringExpenseIdGenerator
import com.kyovo.cents.domain.port.output.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.util.UUID

fun aRecurringExpenseId(value: String = "66666666-6666-6666-6666-666666666666"): RecurringExpenseId
{
    return RecurringExpenseId(UUID.fromString(value))
}

fun aRecurringExpense(
    id: RecurringExpenseId = aRecurringExpenseId(),
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
): RecurringExpense
{
    return RecurringExpense(
        id, accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate, lastGeneratedDate
    )
}

fun aCreateRecurringExpenseCommand(
    accountId: AccountId = anAccountId(),
    amount: Money = aMoney(80_000),
    title: TransactionTitle = aTransactionTitle("Loyer"),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    startDate: LocalDate = LocalDate.of(2026, 9, 5),
    endDate: LocalDate? = null,
): CreateRecurringExpenseCommand
{
    return CreateRecurringExpenseCommand(
        accountId, amount, title, subcategoryId, description, frequency, interval, startDate, endDate
    )
}

fun anUpdateRecurringExpenseCommand(
    id: RecurringExpenseId = aRecurringExpenseId(),
    amount: Money = aMoney(80_000),
    title: TransactionTitle = aTransactionTitle("Loyer"),
    subcategoryId: SubcategoryId? = null,
    description: TransactionDescription? = null,
    frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    interval: Int = 1,
    endDate: LocalDate? = null,
): UpdateRecurringExpenseCommand
{
    return UpdateRecurringExpenseCommand(id, amount, title, subcategoryId, description, frequency, interval, endDate)
}

class FixedRecurringExpenseIdGenerator(private val id: RecurringExpenseId) : RecurringExpenseIdGenerator
{
    override fun generate(): RecurringExpenseId = id
}

class InMemoryRecurringExpenseRepository : RecurringExpenseRepository
{
    private val state = MutableStateFlow<List<RecurringExpense>>(emptyList())

    /** What is stored, in order (for the tests to look at). */
    val saved: List<RecurringExpense> get() = state.value

    override suspend fun save(recurringExpense: RecurringExpense)
    {
        val current = state.value
        val index = current.indexOfFirst { it.id == recurringExpense.id }
        state.value =
            if (index >= 0) current.toMutableList().also { it[index] = recurringExpense } else current + recurringExpense
    }

    override suspend fun findById(id: RecurringExpenseId): RecurringExpense?
    {
        return state.value.find { it.id == id }
    }

    override suspend fun findAll(): List<RecurringExpense>
    {
        return state.value
    }

    override suspend fun deleteById(id: RecurringExpenseId)
    {
        state.value = state.value.filterNot { it.id == id }
    }

    override fun observeAll(): Flow<List<RecurringExpense>>
    {
        return state
    }
}
