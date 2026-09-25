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
