package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.aCurrency
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.port.input.UpdateAccountCommand
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UpdateAccountServiceTest
{
    @Test
    fun `updates the name and type of an existing account while keeping its currency and creation instant`()
    {
        // GIVEN
        val id = anAccountId()
        val currency = aCurrency("USD")
        val createdAt = anInstant("2025-03-10T08:00:00Z")
        val repository = InMemoryAccountRepository()
        repository.save(
            anAccount(
                id = id,
                name = AccountName("Livret A"),
                type = AccountType.CHECKING,
                currency = currency,
                createdAt = createdAt
            )
        )
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(id = id, name = AccountName("Livret B"), type = AccountType.SAVINGS)

        // WHEN
        val result = service.update(command)

        // THEN
        assertThat(result).isEqualTo(
            anAccount(
                id = id,
                name = AccountName("Livret B"),
                type = AccountType.SAVINGS,
                currency = currency,
                createdAt = createdAt
            )
        )
        assertThat(repository.saved).containsExactly(result)
    }

    @Test
    fun `throws when no account matches the given id`()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(
            id = anAccountId(),
            name = AccountName("Livret A"),
            type = AccountType.CHECKING
        )

        // WHEN / THEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the new name is already used by a different account`()
    {
        // GIVEN
        val idToUpdate = anAccountId()
        val otherId = anAccountId("22222222-2222-2222-2222-222222222222")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = idToUpdate, name = AccountName("Livret A")))
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant")))
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(
            id = idToUpdate,
            name = AccountName("Compte courant"),
            type = AccountType.CHECKING
        )

        // WHEN / THEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @Test
    fun `does not save the account when the new name is already used by a different account`()
    {
        // GIVEN
        val idToUpdate = anAccountId()
        val otherId = anAccountId("22222222-2222-2222-2222-222222222222")
        val repository = InMemoryAccountRepository()
        val original = anAccount(id = idToUpdate, name = AccountName("Livret A"))
        repository.save(original)
        repository.save(anAccount(id = otherId, name = AccountName("Compte courant")))
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(
            id = idToUpdate,
            name = AccountName("Compte courant"),
            type = AccountType.CHECKING
        )

        // WHEN
        assertThatThrownBy { service.update(command) }
            .isInstanceOf(DuplicateAccountNameException::class.java)

        // THEN
        assertThat(repository.saved).contains(original)
    }

    @Test
    fun `allows keeping the account's own name, even with a different case, when updating its type`()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), type = AccountType.CHECKING))
        val service = UpdateAccountService(repository)
        val command = UpdateAccountCommand(id = id, name = AccountName("livret a"), type = AccountType.SAVINGS)

        // WHEN
        val result = service.update(command)

        // THEN
        assertThat(result.type).isEqualTo(AccountType.SAVINGS)
    }
}
