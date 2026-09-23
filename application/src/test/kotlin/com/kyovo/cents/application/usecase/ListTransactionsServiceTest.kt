package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListTransactionsServiceTest
{
    @Test
    fun `returns all transactions when no date range is given`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val first = aTransaction(id = aTransactionId("11111111-1111-1111-1111-111111111111"))
        val second = aTransaction(id = aTransactionId("22222222-2222-2222-2222-222222222222"))
        repository.save(first)
        repository.save(second)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list()

        // THEN
        assertThat(result).containsExactly(first, second)
    }

    @Test
    fun `returns only transactions on or after the given start date when no end date is given`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val early = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            date = anInstant("2026-01-01T00:00:00Z")
        )
        val late = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            date = anInstant("2026-06-01T00:00:00Z")
        )
        repository.save(early)
        repository.save(late)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(from = anInstant("2026-03-01T00:00:00Z"))

        // THEN
        assertThat(result).containsExactly(late)
    }

    @Test
    fun `returns only transactions on or before the given end date when no start date is given`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val early = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            date = anInstant("2026-01-01T00:00:00Z")
        )
        val late = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            date = anInstant("2026-06-01T00:00:00Z")
        )
        repository.save(early)
        repository.save(late)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(to = anInstant("2026-03-01T00:00:00Z"))

        // THEN
        assertThat(result).containsExactly(early)
    }

    @Test
    fun `returns only transactions within the given date range, boundaries included`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val before = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            date = anInstant("2026-01-01T00:00:00Z")
        )
        val onStart = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            date = anInstant("2026-02-01T00:00:00Z")
        )
        val onEnd = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333333"),
            date = anInstant("2026-03-01T00:00:00Z")
        )
        val after = aTransaction(
            id = aTransactionId("44444444-4444-4444-4444-444444444444"),
            date = anInstant("2026-04-01T00:00:00Z")
        )
        repository.save(before)
        repository.save(onStart)
        repository.save(onEnd)
        repository.save(after)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(
            from = anInstant("2026-02-01T00:00:00Z"),
            to = anInstant("2026-03-01T00:00:00Z")
        )

        // THEN
        assertThat(result).containsExactly(onStart, onEnd)
    }

    @Test
    fun `returns an empty list when no transaction falls within the given date range`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(date = anInstant("2026-01-01T00:00:00Z")))
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(
            from = anInstant("2026-06-01T00:00:00Z"),
            to = anInstant("2026-07-01T00:00:00Z")
        )

        // THEN
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns only transactions belonging to the given account`()
    {
        // GIVEN
        val accountId = anAccountId("11111111-1111-1111-1111-111111111111")
        val otherAccountId = anAccountId("22222222-2222-2222-2222-222222222222")
        val repository = InMemoryTransactionRepository()
        val ownTransaction = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333333"),
            accountId = accountId
        )
        val otherTransaction = aTransaction(
            id = aTransactionId("44444444-4444-4444-4444-444444444444"),
            accountId = otherAccountId
        )
        repository.save(ownTransaction)
        repository.save(otherTransaction)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(accountId = accountId)

        // THEN
        assertThat(result).containsExactly(ownTransaction)
    }

    @Test
    fun `returns only transactions of the given category`()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val expense = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            category = TransactionCategory.EXPENSE
        )
        val income = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            category = TransactionCategory.INCOME
        )
        repository.save(expense)
        repository.save(income)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(category = TransactionCategory.EXPENSE)

        // THEN
        assertThat(result).containsExactly(expense)
    }

    @Test
    fun `combines account, category and date range filters`()
    {
        // GIVEN
        val accountId = anAccountId("11111111-1111-1111-1111-111111111111")
        val repository = InMemoryTransactionRepository()
        val matching = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val wrongAccount = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333333"),
            accountId = anAccountId("44444444-4444-4444-4444-444444444444"),
            category = TransactionCategory.EXPENSE,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val wrongCategory = aTransaction(
            id = aTransactionId("55555555-5555-5555-5555-555555555555"),
            accountId = accountId,
            category = TransactionCategory.INCOME,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val outsideRange = aTransaction(
            id = aTransactionId("66666666-6666-6666-6666-666666666666"),
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            date = anInstant("2026-05-01T00:00:00Z")
        )
        repository.save(matching)
        repository.save(wrongAccount)
        repository.save(wrongCategory)
        repository.save(outsideRange)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.list(
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            from = anInstant("2026-01-01T00:00:00Z"),
            to = anInstant("2026-03-01T00:00:00Z")
        )

        // THEN
        assertThat(result).containsExactly(matching)
    }
}
