package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * What a screen collecting the accounts sees: the stored list now, then every change to it, in the
 * order the user arranged the accounts. Stands in for the Flow a Room DAO will return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListAccountRepositoryObserveTest
{
    private val repository = ListAccountRepository()

    private val idA = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val idB = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
    private val idC = AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"))

    private fun anAccount(id: AccountId, name: String) = Account(
        id = id,
        name = AccountName(name),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-09-22T10:00:00Z"),
    )

    private fun observeNames(scope: kotlinx.coroutines.test.TestScope): List<List<String>>
    {
        val seen = mutableListOf<List<String>>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler))
        {
            repository.observeAll().collect { list -> seen += list.map { it.name.value } }
        }
        return seen
    }

    @Test
    fun `an observer first gets what is stored, in order`() = runTest()
    {
        // GIVEN
        val a = anAccount(idA, "A")
        val b = anAccount(idB, "B")
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
    fun `an observer sees every change as it happens, in the stored order`() = runTest()
    {
        // GIVEN an observer
        val seen = observeNames(this)

        // WHEN
        repository.save(anAccount(idA, "A"))
        repository.save(anAccount(idB, "B"))
        repository.save(anAccount(idA, "Renamed"))
        repository.deleteById(idA)

        // THEN a rename stays where it stands
        assertThat(seen).containsExactly(
            emptyList(),
            listOf("A"),
            listOf("A", "B"),
            listOf("Renamed", "B"),
            listOf("B"),
        )
    }

    @Test
    fun `a reorder reaches an observer in one step, never half done`() = runTest()
    {
        // GIVEN
        repository.save(anAccount(idA, "A"))
        repository.save(anAccount(idB, "B"))
        repository.save(anAccount(idC, "C"))
        val seen = observeNames(this)

        // WHEN
        repository.reorder(listOf(idC, idB, idA))

        // THEN
        assertThat(seen).containsExactly(listOf("A", "B", "C"), listOf("C", "B", "A"))
    }

    @Test
    fun `an observer collecting late still gets the current state`() = runTest()
    {
        // GIVEN changes made before anyone observes
        repository.save(anAccount(idA, "A"))
        repository.save(anAccount(idB, "B"))
        repository.deleteById(idA)

        // WHEN / THEN
        assertThat(repository.observeAll().first().map { it.name.value }).containsExactly("B")
    }
}
