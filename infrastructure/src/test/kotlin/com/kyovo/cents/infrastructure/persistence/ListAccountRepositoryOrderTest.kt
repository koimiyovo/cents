package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.test.runTest
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/**
 * The repository keeps the accounts in an order the user chose, and `findAll()` returns them in it.
 * That order is only ever changed on purpose: adding an account appends it, and saving an account
 * again (a rename, an archival...) must not send it to the end of the list.
 */
class ListAccountRepositoryOrderTest
{
    private val idA = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val idB = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
    private val idC = AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val idD = AccountId(UUID.fromString("44444444-4444-4444-4444-444444444444"))

    private fun anAccount(id: AccountId, name: String = "Compte ${id.value}") = Account(
        id = id,
        name = AccountName(name),
        type = AccountType.CHECKING,
        currency = AccountCurrency(Currency.getInstance("EUR")),
        createdAt = Instant.parse("2026-09-22T10:00:00Z"),
    )

    private suspend fun repositoryWith(ids: List<AccountId>): ListAccountRepository
    {
        val repository = ListAccountRepository()
        ids.forEach { repository.save(anAccount(it)) }
        return repository
    }

    private suspend fun ListAccountRepository.ids(): List<AccountId> = findAll().map { it.id }

    @Test
    fun `a new account is added at the end`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB))

        // WHEN
        repository.save(anAccount(idC))

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `saving an existing account again keeps its position`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB, idC))

        // WHEN
        repository.save(anAccount(idB, name = "Renommé"))

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
        assertThat(repository.findById(idB)!!.name).isEqualTo(AccountName("Renommé"))
    }

    @Test
    fun `saving an existing account again does not duplicate it`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB))

        // WHEN
        repository.save(anAccount(idA, name = "Renommé"))

        // THEN
        assertThat(repository.findAll()).hasSize(2)
    }

    @Test
    fun `reordering puts the accounts in the given order`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB, idC))

        // WHEN
        repository.reorder(listOf(idC, idA, idB))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idA, idB)
    }

    @Test
    fun `reordering does not alter the accounts themselves`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB, idC))
        val before = repository.findAll().associateBy { it.id }

        // WHEN
        repository.reorder(listOf(idC, idB, idA))

        // THEN
        repository.findAll().forEach { assertThat(it).isEqualTo(before.getValue(it.id)) }
    }

    @Test
    fun `reordering leaves the accounts that are not listed where they are`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idD, idB, idC))

        // WHEN
        repository.reorder(listOf(idC, idB, idA))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idD, idB, idA)
    }

    @Test
    fun `reordering only some of the accounts shuffles them among their own positions`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB, idC))

        // WHEN
        repository.reorder(listOf(idC, idA))

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idB, idA)
    }

    @Test
    fun `reordering with an empty list changes nothing`() = runTest()
    {
        // GIVEN
        val repository = repositoryWith(listOf(idA, idB, idC))

        // WHEN
        repository.reorder(emptyList())

        // THEN
        assertThat(repository.ids()).containsExactly(idA, idB, idC)
    }

    @Test
    fun `the order survives a snapshot and restore`() = runTest()
    {
        // GIVEN a failed multi-write is rolled back with these, and must not scramble the list
        val repository = repositoryWith(listOf(idA, idB, idC))
        repository.reorder(listOf(idC, idB, idA))
        val snapshot = repository.snapshot()

        // WHEN
        repository.save(anAccount(idD))
        repository.restore(snapshot)

        // THEN
        assertThat(repository.ids()).containsExactly(idC, idB, idA)
    }
}
