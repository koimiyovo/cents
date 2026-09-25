package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The active accounts are *observed*: the screens collect a Flow that emits the list now and again
 * whenever it changes, in the order the user arranged them (the repository's stored order, no sorting
 * here). Archived accounts are never part of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListAccountsServiceTest
{
    private val repository = InMemoryAccountRepository()
    private val service = ListAccountsService(repository)

    private fun aNamed(suffix: Int, name: String, archived: Boolean = false): Account = anAccount(
        id = anAccountId("11111111-1111-1111-1111-11111111111$suffix"),
        name = AccountName(name),
        archivedAt = if (archived) anInstant() else null,
    )

    @Test
    fun `emits every account when no filter is given`() = runTest()
    {
        // GIVEN
        val livretA = aNamed(1, "Livret A")
        val compteCourant = aNamed(2, "Compte courant")
        repository.save(livretA)
        repository.save(compteCourant)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(livretA, compteCourant)
    }

    @Test
    fun `emits an empty list when there is no account`() = runTest()
    {
        assertThat(service.observe().first()).isEmpty()
    }

    @Test
    fun `emits only the accounts whose name contains the filter, ignoring case`() = runTest()
    {
        // GIVEN
        val livretA = aNamed(1, "Livret A")
        repository.save(livretA)
        repository.save(aNamed(2, "Compte courant"))

        // WHEN / THEN
        assertThat(service.observe("livret").first()).containsExactly(livretA)
    }

    @Test
    fun `emits every account matching the filter when several match`() = runTest()
    {
        // GIVEN
        val livretA = aNamed(1, "Livret A")
        val livretB = aNamed(2, "Livret B")
        repository.save(livretA)
        repository.save(livretB)
        repository.save(aNamed(3, "Compte courant"))

        // WHEN / THEN
        assertThat(service.observe("livret").first()).containsExactly(livretA, livretB)
    }

    @Test
    fun `emits an empty list when no account matches the filter`() = runTest()
    {
        // GIVEN
        repository.save(aNamed(1, "Livret A"))

        // WHEN / THEN
        assertThat(service.observe("Compte").first()).isEmpty()
    }

    @Test
    fun `leaves archived accounts out`() = runTest()
    {
        // GIVEN
        val active = aNamed(1, "Livret A")
        repository.save(active)
        repository.save(aNamed(2, "Compte courant", archived = true))

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(active)
    }

    // ------------------------------------------------------------------ it keeps emitting

    @Test
    fun `emits again, in stored order, each time an account is added, renamed, archived or deleted`() = runTest()
    {
        // GIVEN a screen collecting the accounts
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.save(aNamed(1, "Livret A"))
        repository.save(aNamed(2, "Compte courant"))
        repository.save(aNamed(1, "Livret B"))
        repository.save(aNamed(2, "Compte courant", archived = true))
        repository.deleteById(aNamed(1, "Livret B").id)

        // THEN every visible state was seen
        assertThat(emissions).containsExactly(
            emptyList(),
            listOf("Livret A"),
            listOf("Livret A", "Compte courant"),
            listOf("Livret B", "Compte courant"),
            listOf("Livret B"),
            emptyList(),
        )
    }

    @Test
    fun `an unarchived account comes back into the list`() = runTest()
    {
        // GIVEN
        val archived = aNamed(1, "Livret A", archived = true)
        repository.save(archived)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.save(archived.copy(archivedAt = null))

        // THEN
        assertThat(emissions).containsExactly(emptyList(), listOf("Livret A"))
    }

    @Test
    fun `follows the order the user gave the accounts`() = runTest()
    {
        // GIVEN
        val a = aNamed(1, "A")
        val b = aNamed(2, "B")
        val c = aNamed(3, "C")
        repository.save(a)
        repository.save(b)
        repository.save(c)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.reorder(listOf(c.id, a.id, b.id))

        // THEN
        assertThat(emissions).containsExactly(listOf("A", "B", "C"), listOf("C", "A", "B"))
    }

    // A screen showing the active accounts has nothing to redraw when a closed one is edited.
    @Test
    fun `a change to an archived account does not make the list emit again`() = runTest()
    {
        // GIVEN a screen collecting the active accounts
        val archived = aNamed(2, "Ancien compte", archived = true)
        repository.save(aNamed(1, "Livret A"))
        repository.save(archived)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN the archived one is renamed
        repository.save(archived.copy(name = AccountName("Vieux compte")))

        // THEN
        assertThat(emissions).containsExactly(listOf("Livret A"))
    }

    @Test
    fun `with a filter, emits again only when the matching accounts change`() = runTest()
    {
        // GIVEN a screen searching "livret"
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe("livret").collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN an account that does not match is added, then one that does
        repository.save(aNamed(1, "Compte courant"))
        repository.save(aNamed(2, "Livret A"))

        // THEN
        assertThat(emissions).containsExactly(emptyList(), listOf("Livret A"))
    }
}
