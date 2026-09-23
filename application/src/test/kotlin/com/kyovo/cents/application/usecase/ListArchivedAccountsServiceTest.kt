package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListArchivedAccountsServiceTest
{
    @Test
    fun `returns only archived accounts`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val active = anAccount(name = AccountName("Livret A"))
        val archived = anAccount(
            id = anAccountId("22222222-2222-2222-2222-222222222222"),
            name = AccountName("Compte courant"),
            archivedAt = anInstant()
        )
        repository.save(active)
        repository.save(archived)
        val service = ListArchivedAccountsService(repository)

        // WHEN
        val result = service.list()

        // THEN
        assertThat(result).containsExactly(archived)
    }

    @Test
    fun `returns an empty list when no account is archived`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount())
        val service = ListArchivedAccountsService(repository)

        // WHEN
        val result = service.list()

        // THEN
        assertThat(result).isEmpty()
    }
}
