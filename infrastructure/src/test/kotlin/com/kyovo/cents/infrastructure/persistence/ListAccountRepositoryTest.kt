package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

class ListAccountRepositoryTest
{
    @Test
    fun `does not consider a name used when no account has been saved`()
    {
        // GIVEN
        val repository = ListAccountRepository()

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isFalse()
    }

    @Test
    fun `considers a name used once an account with that name has been saved`()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Livret A"))).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["livret a", "  Livret A  ", "LIVRET A"])
    fun `considers a name used regardless of case or surrounding whitespace`(nameVariant: String)
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName(nameVariant))).isTrue()
    }

    @Test
    fun `does not consider an unrelated name used`()
    {
        // GIVEN
        val repository = ListAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))

        // WHEN / THEN
        assertThat(repository.existsByName(AccountName("Compte courant"))).isFalse()
    }

    @Test
    fun `replaces an existing account when saving another account with the same id`()
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
    fun `no longer finds an account once it has been deleted`()
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
    fun `does not throw when deleting an account that does not exist`()
    {
        // GIVEN
        val repository = ListAccountRepository()

        // WHEN / THEN
        assertThatCode { repository.deleteById(AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))) }
            .doesNotThrowAnyException()
    }

    private fun anAccount(name: AccountName): Account
    {
        return Account(
            id = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111")),
            name = name,
            type = AccountType.CHECKING,
            currency = AccountCurrency(Currency.getInstance("EUR")),
            createdAt = Instant.parse("2026-09-22T10:00:00Z")
        )
    }
}
