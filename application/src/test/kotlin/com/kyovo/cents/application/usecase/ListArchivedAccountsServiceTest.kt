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

@OptIn(ExperimentalCoroutinesApi::class)
class ListArchivedAccountsServiceTest
{
    private val repository = InMemoryAccountRepository()
    private val service = ListArchivedAccountsService(repository)

    private fun aNamed(suffix: Int, name: String, archived: Boolean): Account = anAccount(
        id = anAccountId("11111111-1111-1111-1111-11111111111$suffix"),
        name = AccountName(name),
        archivedAt = if (archived) anInstant() else null,
    )

    @Test
    fun `emits only archived accounts`() = runTest()
    {
        // GIVEN
        val archived = aNamed(2, "Compte courant", archived = true)
        repository.save(aNamed(1, "Livret A", archived = false))
        repository.save(archived)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(archived)
    }

    @Test
    fun `emits an empty list when no account is archived`() = runTest()
    {
        // GIVEN
        repository.save(aNamed(1, "Livret A", archived = false))

        // WHEN / THEN
        assertThat(service.observe().first()).isEmpty()
    }

    @Test
    fun `emits again when an account is archived or unarchived`() = runTest()
    {
        // GIVEN a screen collecting the archived accounts
        val account = aNamed(1, "Livret A", archived = false)
        repository.save(account)
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.save(account.copy(archivedAt = anInstant()))
        repository.save(account)

        // THEN
        assertThat(emissions).containsExactly(emptyList(), listOf("Livret A"), emptyList())
    }

    // The folded "Comptes archivés" line has nothing to redraw when an active account changes.
    @Test
    fun `a change to an active account does not make the list emit again`() = runTest()
    {
        // GIVEN
        val active = aNamed(1, "Livret A", archived = false)
        repository.save(active)
        repository.save(aNamed(2, "Ancien compte", archived = true))
        val emissions = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe().collect { list -> emissions += list.map { it.name.value } }
        }

        // WHEN
        repository.save(active.copy(name = AccountName("Livret B")))

        // THEN
        assertThat(emissions).containsExactly(listOf("Ancien compte"))
    }
}
