package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.application.fakes.aMoney
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransactionTitle
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.TransactionCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListTransactionsServiceTest
{
    private val groceriesId = aSubcategoryId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val fuelId = aSubcategoryId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    @Test
    fun `returns all transactions when no date range is given`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val first = aTransaction(id = aTransactionId("11111111-1111-1111-1111-111111111111"))
        val second = aTransaction(id = aTransactionId("22222222-2222-2222-2222-222222222222"))
        repository.save(first)
        repository.save(second)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.observe().first()

        // THEN
        assertThat(result).containsExactly(first, second)
    }

    @Test
    fun `returns only transactions on or after the given start date when no end date is given`() = runTest()
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
        val result = service.observe(from = anInstant("2026-03-01T00:00:00Z")).first()

        // THEN
        assertThat(result).containsExactly(late)
    }

    @Test
    fun `returns only transactions on or before the given end date when no start date is given`() = runTest()
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
        val result = service.observe(to = anInstant("2026-03-01T00:00:00Z")).first()

        // THEN
        assertThat(result).containsExactly(early)
    }

    @Test
    fun `returns only transactions within the given date range, boundaries included`() = runTest()
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
        val result = service.observe(
            from = anInstant("2026-02-01T00:00:00Z"),
            to = anInstant("2026-03-01T00:00:00Z")
        ).first()

        // THEN
        assertThat(result).containsExactly(onStart, onEnd)
    }

    @Test
    fun `returns an empty list when no transaction falls within the given date range`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        repository.save(aTransaction(date = anInstant("2026-01-01T00:00:00Z")))
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.observe(
            from = anInstant("2026-06-01T00:00:00Z"),
            to = anInstant("2026-07-01T00:00:00Z")
        ).first()

        // THEN
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns only transactions belonging to the given account`() = runTest()
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
        val result = service.observe(accountId = accountId).first()

        // THEN
        assertThat(result).containsExactly(ownTransaction)
    }

    @Test
    fun `returns only transactions of the given subcategory`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val groceries = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            category = TransactionCategory.EXPENSE,
            subcategoryId = groceriesId
        )
        val fuel = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            category = TransactionCategory.EXPENSE,
            subcategoryId = fuelId
        )
        repository.save(groceries)
        repository.save(fuel)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.observe(subcategoryId = groceriesId).first()

        // THEN
        assertThat(result).containsExactly(groceries)
    }

    @Test
    fun `combines account, subcategory and date range filters`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId("11111111-1111-1111-1111-111111111111")
        val repository = InMemoryTransactionRepository()
        val matching = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            subcategoryId = groceriesId,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val wrongAccount = aTransaction(
            id = aTransactionId("33333333-3333-3333-3333-333333333333"),
            accountId = anAccountId("44444444-4444-4444-4444-444444444444"),
            category = TransactionCategory.EXPENSE,
            subcategoryId = groceriesId,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val wrongSubcategory = aTransaction(
            id = aTransactionId("55555555-5555-5555-5555-555555555555"),
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            subcategoryId = fuelId,
            date = anInstant("2026-02-15T00:00:00Z")
        )
        val outsideRange = aTransaction(
            id = aTransactionId("66666666-6666-6666-6666-666666666666"),
            accountId = accountId,
            category = TransactionCategory.EXPENSE,
            subcategoryId = groceriesId,
            date = anInstant("2026-05-01T00:00:00Z")
        )
        repository.save(matching)
        repository.save(wrongAccount)
        repository.save(wrongSubcategory)
        repository.save(outsideRange)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.observe(
            accountId = accountId,
            subcategoryId = groceriesId,
            from = anInstant("2026-01-01T00:00:00Z"),
            to = anInstant("2026-03-01T00:00:00Z")
        ).first()

        // THEN
        assertThat(result).containsExactly(matching)
    }

    @Test
    fun `returns only transactions whose title contains the given text, case-insensitively`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val groceries = aTransaction(
            id = aTransactionId("11111111-1111-1111-1111-111111111111"),
            category = TransactionCategory.EXPENSE,
            title = aTransactionTitle("Courses de la semaine")
        )
        val salary = aTransaction(
            id = aTransactionId("22222222-2222-2222-2222-222222222222"),
            category = TransactionCategory.INCOME,
            title = aTransactionTitle("Salaire")
        )
        repository.save(groceries)
        repository.save(salary)
        val service = ListTransactionsService(repository)

        // WHEN
        val result = service.observe(titleFilter = "courses").first()

        // THEN
        assertThat(result).containsExactly(groceries)
    }

    // ------------------------------------------------------------------ it keeps emitting

    @Test
    fun `emits again each time a transaction is recorded or deleted`() = runTest()
    {
        // GIVEN a screen collecting the transactions
        val repository = InMemoryTransactionRepository()
        val service = ListTransactionsService(repository)
        val first = aTransaction(id = aTransactionId("11111111-1111-1111-1111-111111111111"))
        val second = aTransaction(id = aTransactionId("22222222-2222-2222-2222-222222222222"))
        val emissions = mutableListOf<List<TransactionId>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.id } }
        }

        // WHEN
        repository.save(first)
        repository.save(second)
        repository.deleteById(first.id)

        // THEN
        assertThat(emissions).containsExactly(
            emptyList(),
            listOf(first.id),
            listOf(first.id, second.id),
            listOf(second.id),
        )
    }

    @Test
    fun `emits again when a transaction is edited`() = runTest()
    {
        // GIVEN
        val repository = InMemoryTransactionRepository()
        val service = ListTransactionsService(repository)
        val id = aTransactionId("11111111-1111-1111-1111-111111111111")
        repository.save(aTransaction(id = id, amount = aMoney(1_000)))
        val amounts = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> amounts += list.map { it.amount.value } }
        }

        // WHEN
        repository.save(aTransaction(id = id, amount = aMoney(2_500)))

        // THEN
        assertThat(amounts).containsExactly(listOf(1_000L), listOf(2_500L))
    }

    // A page showing one account has nothing to redraw when a transaction is recorded on another.
    @Test
    fun `with a filter, emits again only when the matching transactions change`() = runTest()
    {
        // GIVEN a page on one account
        val repository = InMemoryTransactionRepository()
        val service = ListTransactionsService(repository)
        val mine = anAccountId("11111111-1111-1111-1111-111111111111")
        val other = anAccountId("22222222-2222-2222-2222-222222222222")
        val emissions = mutableListOf<List<TransactionId>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(accountId = mine).collect { list -> emissions += list.map { it.id } }
        }

        // WHEN a transaction lands on the other account, then one on this one
        repository.save(aTransaction(id = aTransactionId("33333333-3333-3333-3333-333333333333"), accountId = other))
        val minePlease = aTransaction(id = aTransactionId("44444444-4444-4444-4444-444444444444"), accountId = mine)
        repository.save(minePlease)

        // THEN
        assertThat(emissions).containsExactly(emptyList(), listOf(minePlease.id))
    }
}
