package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListAccountsServiceTest
{
    @Test
    fun `returns all accounts when no filter is given`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val livretA = anAccount(name = AccountName("Livret A"))
        val compteCourant = anAccount(
            id = anAccountId("22222222-2222-2222-2222-222222222222"),
            name = AccountName("Compte courant")
        )
        repository.save(livretA)
        repository.save(compteCourant)
        val service = ListAccountsService(repository)

        // WHEN
        val result = service.list()

        // THEN
        assertThat(result).containsExactly(livretA, compteCourant)
    }

    @Test
    fun `returns only accounts whose name contains the filter, ignoring case`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val livretA = anAccount(name = AccountName("Livret A"))
        val compteCourant = anAccount(
            id = anAccountId("22222222-2222-2222-2222-222222222222"),
            name = AccountName("Compte courant")
        )
        repository.save(livretA)
        repository.save(compteCourant)
        val service = ListAccountsService(repository)

        // WHEN
        val result = service.list("livret")

        // THEN
        assertThat(result).containsExactly(livretA)
    }

    @Test
    fun `returns an empty list when no account matches the filter`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(name = AccountName("Livret A")))
        val service = ListAccountsService(repository)

        // WHEN
        val result = service.list("Compte")

        // THEN
        assertThat(result).isEmpty()
    }
}
