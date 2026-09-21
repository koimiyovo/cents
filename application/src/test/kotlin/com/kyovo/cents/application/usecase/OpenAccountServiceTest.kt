package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedAccountIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.aClock
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.application.fakes.anOpenAccountCommand
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenAccountServiceTest
{
    @Test
    fun `opens an account with the given name, type and currency, a generated id and the current instant, and saves it`()
    {
        // GIVEN
        val generatedId = anAccountId()
        val now = anInstant()
        val repository = InMemoryAccountRepository()
        val service = OpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
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
        val service = OpenAccountService(repository, FixedAccountIdGenerator(generatedId), aClock(now))
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
}
