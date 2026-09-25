package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountNotArchivedException
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UnarchiveAccountServiceTest
{
    private val archivedAt = anInstant("2026-01-01T00:00:00Z")

    @Test
    fun `unarchives an archived account by clearing its archival instant`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, archivedAt = archivedAt))
        val service = UnarchiveAccountService(repository)

        // WHEN
        val result = service.unarchive(id)

        // THEN
        assertThat(result).isEqualTo(anAccount(id = id, archivedAt = null))
        assertThat(repository.saved).containsExactly(result)
    }

    @Test
    fun `keeps every other field of the account as it was`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val archived = anAccount(
            id = id,
            name = AccountName("Épargne"),
            type = AccountType.SAVINGS,
            createdAt = anInstant("2025-03-01T08:00:00Z"),
            archivedAt = archivedAt
        ).copy(description = AccountDescription.of("Épargne de précaution"))
        val repository = InMemoryAccountRepository()
        repository.save(archived)
        val service = UnarchiveAccountService(repository)

        // WHEN
        val result = service.unarchive(id)

        // THEN
        assertThat(result).isEqualTo(archived.copy(archivedAt = null))
    }

    @Test
    fun `only touches the account it is given`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val other = anAccount(id = anAccountId("22222222-2222-2222-2222-222222222222"), name = AccountName("Compte courant"))
        val alsoArchived = anAccount(
            id = anAccountId("33333333-3333-3333-3333-333333333333"),
            name = AccountName("Vieux compte"),
            archivedAt = archivedAt
        )
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, archivedAt = archivedAt))
        repository.save(other)
        repository.save(alsoArchived)
        val service = UnarchiveAccountService(repository)

        // WHEN
        service.unarchive(id)

        // THEN
        assertThat(repository.findById(other.id)).isEqualTo(other)
        assertThat(repository.findById(alsoArchived.id)).isEqualTo(alsoArchived)
    }

    @Test
    fun `throws when no account matches the given id`() = runTest()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = UnarchiveAccountService(repository)

        // WHEN / THEN
        assertThatThrownBySuspending { service.unarchive(anAccountId()) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the account is not archived`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id))
        val service = UnarchiveAccountService(repository)

        // WHEN / THEN
        assertThatThrownBySuspending { service.unarchive(id) }
            .isInstanceOf(AccountNotArchivedException::class.java)
    }

    @Test
    fun `does not change the account when it is not archived`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val account = anAccount(id = id)
        val repository = InMemoryAccountRepository()
        repository.save(account)
        val service = UnarchiveAccountService(repository)

        // WHEN
        assertThatThrownBySuspending { service.unarchive(id) }
            .isInstanceOf(AccountNotArchivedException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(account)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Livret A", "livret a", "  livret A  "])
    fun `refuses to unarchive an account whose name is now used by an active account`(
        duplicateNameVariant: String
    ) = runTest()
    {
        // GIVEN the name was reused after the first account got archived
        val archivedId = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = archivedId, name = AccountName("Livret A"), archivedAt = archivedAt))
        repository.save(
            anAccount(
                id = anAccountId("22222222-2222-2222-2222-222222222222"),
                name = AccountName(duplicateNameVariant)
            )
        )
        val service = UnarchiveAccountService(repository)

        // WHEN / THEN
        assertThatThrownBySuspending { service.unarchive(archivedId) }
            .isInstanceOf(DuplicateAccountNameException::class.java)
    }

    @Test
    fun `keeps the account archived when its name is now used by an active account`() = runTest()
    {
        // GIVEN
        val archived = anAccount(name = AccountName("Livret A"), archivedAt = archivedAt)
        val active = anAccount(id = anAccountId("22222222-2222-2222-2222-222222222222"), name = AccountName("Livret A"))
        val repository = InMemoryAccountRepository()
        repository.save(archived)
        repository.save(active)
        val service = UnarchiveAccountService(repository)

        // WHEN
        assertThatThrownBySuspending { service.unarchive(archived.id) }
            .isInstanceOf(DuplicateAccountNameException::class.java)

        // THEN
        assertThat(repository.saved).containsExactlyInAnyOrder(archived, active)
    }

    @Test
    fun `unarchives an account whose name is only shared with other archived accounts`() = runTest()
    {
        // GIVEN archived names never collide: only active accounts must be unique
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, name = AccountName("Livret A"), archivedAt = archivedAt))
        repository.save(
            anAccount(
                id = anAccountId("22222222-2222-2222-2222-222222222222"),
                name = AccountName("Livret A"),
                archivedAt = anInstant("2026-06-01T00:00:00Z")
            )
        )
        val service = UnarchiveAccountService(repository)

        // WHEN
        val result = service.unarchive(id)

        // THEN
        assertThat(result.archivedAt).isNull()
    }
}
