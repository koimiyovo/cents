package com.kyovo.cents.infrastructure.persistence

import kotlinx.coroutines.test.runTest
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

class ListAccountRepositoryTest
{
    @Test
    fun `does not consider a name used when no account has been saved`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
    }

    @Test
    fun `considers a name used once an account with that name has been saved`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["livret a", "  Livret A  ", "LIVRET A"])
    fun `considers a name used regardless of case or surrounding whitespace`(nameVariant: String) = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName(nameVariant))).isTrue()
    }

    @Test
    fun `does not consider an unrelated name used`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Compte courant"))).isFalse()
    }

    @Test
    fun `replaces an existing account when saving another account with the same id`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN
        repository.save(anAccount(name = AccountName("Compte courant")))

        // THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
        assertThat(repository.existsByName(AccountName("Compte courant"))).isTrue()
        assertThat(repository.findAll()).hasSize(1)
    }

    @Test
    fun `no longer finds an account once it has been deleted`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        val account = anAccount(name = AccountName("Livret A"))
        repository.save(account)

        // WHEN
        repository.deleteById(account.id)

        // THEN
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `does not throw when deleting an account that does not exist`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()

        // WHEN / THEN
        repository.deleteById(AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")))
    }

    @Test
    fun `does not consider a name used by an archived account`() = runTest()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(
            anAccount(name = AccountName("Livret A"), archivedAt = Instant.parse("2026-09-23T09:00:00Z"))
        )

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
    }

    private fun anAccount(name: AccountName, archivedAt: Instant? = null): Account
    {
        return Account(
            id = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
            name = name,
            type = AccountType.CHECKING,
            currency = AccountCurrency(Currency.getInstance("EUR")),
            createdAt = Instant.parse("2026-09-22T10:00:00Z"),
            archivedAt = archivedAt
        )
    }
}
