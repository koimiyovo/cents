package com.kyovo.cents.application.usecase

import kotlinx.coroutines.flow.first
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidAccountOrderException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The user arranges the accounts by hand (drag & drop). [ReorderAccountsService] takes the ids in
 * the order the user wants and asks the repository to keep them that way — the order is part of the
 * stored data, so it survives a restart and is what every listing returns.
 *
 * The listed accounts are shuffled *among the positions they already hold*: an account that isn't
 * listed (typically an archived one, which the drag list doesn't show) doesn't move.
 */
class ReorderAccountsServiceTest
{
    private val idA = anAccountId("11111111-1111-1111-1111-111111111111")
    private val idB = anAccountId("22222222-2222-2222-2222-222222222222")
    private val idC = anAccountId("33333333-3333-3333-3333-333333333333")
    private val idArchived = anAccountId("44444444-4444-4444-4444-444444444444")

    private val a = anAccount(id = idA, name = AccountName("A"))
    private val b = anAccount(id = idB, name = AccountName("B"))
    private val c = anAccount(id = idC, name = AccountName("C"))
    private val archived =
        anAccount(id = idArchived, name = AccountName("Archivé"), archivedAt = anInstant("2026-01-01T00:00:00Z"))

    private suspend fun repositoryWith(vararg accounts: Account): InMemoryAccountRepository
    {
        val repository = InMemoryAccountRepository()
        accounts.forEach { repository.save(it) }
        return repository
    }

    private suspend fun InMemoryAccountRepository.ids(): List<AccountId> = findAll().map { it.id }

    @Test
    fun `puts the accounts in the given order`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(listOf(idC, idA, idB))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idA, idB)
    }

    @Test
    fun `does not change the accounts themselves, only their order`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(listOf(idC, idB, idA))

        // THEN
        assertThat(repository.findAll()).containsExactly(c, b, a)
    }

    @Test
    fun `an account that is not listed keeps its place`() = runTest()
    {
        // GIVEN the archived account sits between two active ones and isn't part of the drag list
        val repository = repositoryWith(a, archived, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(listOf(idC, idB, idA))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idArchived, idB, idA)
    }

    @Test
    fun `reordering only some of the accounts shuffles them among their own positions`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN a and c swap, b isn't mentioned
        service.reorder(listOf(idC, idA))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idB, idA)
    }

    @Test
    fun `listing the accounts afterwards follows the new order`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, archived, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(listOf(idB, idC, idA))

        // THEN the archived one is filtered out of the list, the others come in the new order
        assertThat(ListAccountsService(repository).observe().first().map { it.id }).containsExactly(idB, idC, idA)
    }

    @Test
    fun `an empty list changes nothing`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(emptyList())

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `a single account changes nothing`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        service.reorder(listOf(idB))

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `throws when an id matches no account`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b)
        val service = ReorderAccountsService(repository)
        val unknown = anAccountId("99999999-9999-9999-9999-999999999999")

        // WHEN / THEN
        assertThatThrownBySuspending { service.reorder(listOf(idB, unknown, idA)) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `reorders nothing when one of the ids is unknown`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)
        val unknown = anAccountId("99999999-9999-9999-9999-999999999999")

        // WHEN
        assertThatThrownBySuspending { service.reorder(listOf(idC, unknown, idA)) }
            .isInstanceOf(AccountNotFoundException::class.java)

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `throws when the same account is listed twice`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN / THEN
        assertThatThrownBySuspending { service.reorder(listOf(idA, idB, idA)) }
            .isInstanceOf(InvalidAccountOrderException::class.java)
    }

    @Test
    fun `reorders nothing when the same account is listed twice`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(a, b, c)
        val service = ReorderAccountsService(repository)

        // WHEN
        assertThatThrownBySuspending { service.reorder(listOf(idC, idC, idB)) }
            .isInstanceOf(InvalidAccountOrderException::class.java)

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }
}
