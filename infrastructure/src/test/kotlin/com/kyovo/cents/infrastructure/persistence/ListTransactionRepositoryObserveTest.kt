package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * What a screen collecting the transactions sees: the stored list now, then every change to it.
 * Stands in for the Flow a Room DAO will return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListTransactionRepositoryObserveTest
{
    private val repository = ListTransactionRepository()

    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val idA = TransactionId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
    private val idB = TransactionId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))

    private fun aDeposit(id: TransactionId, cents: Long) =
        Transaction.openingDeposit(id, accountId, Money(cents), Instant.parse("2026-09-22T10:00:00Z"))

    @Test
    fun `an observer first gets what is stored`() = runTest()
    {
        // GIVEN
        val a = aDeposit(idA, 1_000)
        val b = aDeposit(idB, 2_000)
        repository.save(a)
        repository.save(b)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(a, b)
    }

    @Test
    fun `an observer of an empty repository gets an empty list`() = runTest()
    {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an observer sees every change as it happens`() = runTest()
    {
        // GIVEN an observer
        val seen = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            repository.observeAll().collect { list -> seen += list.map { it.amount.value } }
        }

        // WHEN
        repository.save(aDeposit(idA, 1_000))
        repository.save(aDeposit(idB, 2_000))
        repository.save(aDeposit(idB, 2_500))
        repository.deleteById(idA)

        // THEN a save of an existing id replaces it
        assertThat(seen).containsExactly(
            emptyList(),
            listOf(1_000L),
            listOf(1_000L, 2_000L),
            listOf(1_000L, 2_500L),
            listOf(2_500L),
        )
    }

    @Test
    fun `an observer collecting late still gets the current state`() = runTest()
    {
        // GIVEN changes made before anyone observes
        repository.save(aDeposit(idA, 1_000))
        repository.save(aDeposit(idB, 2_000))
        repository.deleteById(idA)

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.id }).containsExactly(idB)
    }
}
