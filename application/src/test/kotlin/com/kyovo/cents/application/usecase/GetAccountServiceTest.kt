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
 * Two ways to read one account: [GetAccountService.get] answers once (for an action that needs the
 * account right now, like opening its edit form), [GetAccountService.observe] follows it (for a page
 * showing it), emitting null while there is no such account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetAccountServiceTest
{
    private val repository = InMemoryAccountRepository()
    private val service = GetAccountService(repository)

    @Test
    fun `returns the account matching the given id`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        repository.save(anAccount(id = id))

        // WHEN / THEN
        assertThat(service.get(id)).isEqualTo(anAccount(id = id))
    }

    @Test
    fun `returns the account matching the given id among several saved accounts`() = runTest()
    {
        // GIVEN
        val id = anAccountId("22222222-2222-2222-2222-222222222222")
        repository.save(anAccount())
        repository.save(anAccount(id = id, name = AccountName("Compte courant")))

        // WHEN / THEN
        assertThat(service.get(id)).isEqualTo(anAccount(id = id, name = AccountName("Compte courant")))
    }

    @Test
    fun `returns null when no account matches the given id`() = runTest()
    {
        assertThat(service.get(anAccountId())).isNull()
    }

    // ------------------------------------------------------------------ observed

    @Test
    fun `observing emits the account matching the given id`() = runTest()
    {
        // GIVEN
        val id = anAccountId("22222222-2222-2222-2222-222222222222")
        repository.save(anAccount())
        repository.save(anAccount(id = id, name = AccountName("Compte courant")))

        // WHEN / THEN
        assertThat(service.observe(id).first()).isEqualTo(anAccount(id = id, name = AccountName("Compte courant")))
    }

    @Test
    fun `observing emits null when no account matches the given id`() = runTest()
    {
        assertThat(service.observe(anAccountId()).first()).isNull()
    }

    @Test
    fun `observing follows the account through a rename, an archival and its deletion`() = runTest()
    {
        // GIVEN a page showing the account
        val id = anAccountId()
        val account = anAccount(id = id, name = AccountName("Livret A"))
        repository.save(account)
        val emissions = mutableListOf<Account?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(id).collect { emissions += it }
        }

        // WHEN
        val renamed = account.copy(name = AccountName("Livret B"))
        val archived = renamed.copy(archivedAt = anInstant())
        repository.save(renamed)
        repository.save(archived)
        repository.deleteById(id)

        // THEN
        assertThat(emissions).containsExactly(account, renamed, archived, null)
    }

    @Test
    fun `observing emits the account once it is created after the page opened`() = runTest()
    {
        // GIVEN a page opened on an id nothing has yet
        val id = anAccountId()
        val emissions = mutableListOf<Account?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(id).collect { emissions += it }
        }

        // WHEN
        repository.save(anAccount(id = id))

        // THEN
        assertThat(emissions).containsExactly(null, anAccount(id = id))
    }

    // A page showing one account has nothing to redraw when another one changes.
    @Test
    fun `observing does not emit again for a change to another account`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val other = anAccount(id = anAccountId("22222222-2222-2222-2222-222222222222"), name = AccountName("Compte courant"))
        repository.save(anAccount(id = id))
        repository.save(other)
        val emissions = mutableListOf<Account?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))
        {
            service.observe(id).collect { emissions += it }
        }

        // WHEN
        repository.save(other.copy(name = AccountName("Compte joint")))
        repository.reorder(listOf(other.id, id))

        // THEN
        assertThat(emissions).containsExactly(anAccount(id = id))
    }
}
