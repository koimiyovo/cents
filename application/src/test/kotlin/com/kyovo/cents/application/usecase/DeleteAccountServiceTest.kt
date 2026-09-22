package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.domain.model.AccountName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class DeleteAccountServiceTest
{
    @Test
    fun `deletes an existing account`()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id))
        val service = DeleteAccountService(repository)

        // WHEN
        service.delete(id)

        // THEN
        assertThat(repository.saved).isEmpty()
    }

    @Test
    fun `does not affect other accounts when deleting one of them`()
    {
        // GIVEN
        val idToDelete = anAccountId()
        val otherAccount = anAccount(
            id = anAccountId("22222222-2222-2222-2222-222222222222"),
            name = AccountName("Compte courant")
        )
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = idToDelete))
        repository.save(otherAccount)
        val service = DeleteAccountService(repository)

        // WHEN
        service.delete(idToDelete)

        // THEN
        assertThat(repository.saved).containsExactly(otherAccount)
    }

    @Test
    fun `does not throw when no account matches the given id`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = DeleteAccountService(repository)

        // WHEN / THEN
        assertThatCode { service.delete(anAccountId()) }.doesNotThrowAnyException()
    }
}
