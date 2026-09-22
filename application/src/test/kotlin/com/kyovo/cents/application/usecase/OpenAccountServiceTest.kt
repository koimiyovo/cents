package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedAccountIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.aClock
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anOpenAccountCommand
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OpenAccountServiceTest
{
    @Test
    fun `opens an account with the given name, type and currency, a generated id and the current instant, and saves it`()
    {
        // GIVEN
        val generatedId = anAccountId()
        val now = anInstant()
        val repository = InMemoryAccountRepository()
        val service =
            OpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
        val command = anOpenAccountCommand()
        val expected = anAccount(id = generatedId, createdAt = now)

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `opens another account with its own name, type, currency, generated id and creation instant`()
    {
        // GIVEN
        val generatedId = anAccountId("22222222-2222-2222-2222-222222222222")
        val now = anInstant("2027-01-15T18:30:00Z")
        val repository = InMemoryAccountRepository()
        val service =
            OpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
        val command = anOpenAccountCommand(
            name = AccountName("Compte courant"),
            type = AccountType.SAVINGS,
            currency = aCurrency("USD")
        )
        val expected = anAccount(
            id = generatedId,
            name = AccountName("Compte courant"),
            type = AccountType.SAVINGS,
            currency = aCurrency("USD"),
            createdAt = now
        )

        // WHEN
        service.open(command)

        // THEN
        assertThat(repository.saved).containsExactly(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret A", "livret a", "  livret A  "])
    fun `refuses to open an account whose name is already used by an existing account`(
        duplicateNameVariant: String
    )
    {
        // GIVEN
        val name = AccountName("Livret A")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(name = name))
        val service =
            OpenAccountService(repository, FixedAccountIdGenerator(anAccountId()), aClock())
        val command = anOpenAccountCommand(name = AccountName(duplicateNameVariant))

        // WHEN / THEN
        assertThatThrownBy { service.open(command) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
        assertThat(repository.saved).hasSize(1)
    }
}
