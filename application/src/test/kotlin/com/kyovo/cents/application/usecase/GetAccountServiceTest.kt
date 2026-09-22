package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetAccountServiceTest
{
    @Test
    fun `returns the account matching the given id`()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id))
        val service = GetAccountService(repository)

        // WHEN
        val result = service.get(id)

        // THEN
        assertThat(result).isEqualTo(anAccount(id = id))
    }

    @Test
    fun `returns the account matching the given id among several saved accounts`()
    {
        // GIVEN
        val id = anAccountId("22222222-2222-2222-2222-222222222222")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount())
        repository.save(anAccount(id = id, name = AccountName("Compte courant")))
        val service = GetAccountService(repository)

        // WHEN
        val result = service.get(id)

        // THEN
        assertThat(result).isEqualTo(anAccount(id = id, name = AccountName("Compte courant")))
    }

    @Test
    fun `returns null when no account matches the given id`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = GetAccountService(repository)

        // WHEN
        val result = service.get(anAccountId())

        // THEN
        assertThat(result).isNull()
    }
}
