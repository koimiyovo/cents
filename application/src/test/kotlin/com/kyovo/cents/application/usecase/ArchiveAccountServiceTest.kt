package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import kotlinx.coroutines.test.runTest
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.aClock
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.exception.AccountAlreadyArchivedException
import com.kyovo.cents.domain.exception.AccountNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ArchiveAccountServiceTest
{
    @Test
    fun `archives an existing account, recording the archival instant`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val now = anInstant("2026-09-23T09:00:00Z")
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id))
        val service = ArchiveAccountService(repository, aClock(now))

        // WHEN
        val result = service.archive(id)

        // THEN
        assertThat(result).isEqualTo(anAccount(id = id, archivedAt = now))
        assertThat(repository.saved).containsExactly(result)
    }

    @Test
    fun `throws when no account matches the given id`() = runTest()
    {
        // GIVEN
        val repository = InMemoryAccountRepository()
        val service = ArchiveAccountService(repository, aClock())

        // WHEN / THEN
        assertThatThrownBySuspending { service.archive(anAccountId()) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `throws when the account is already archived`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val repository = InMemoryAccountRepository()
        repository.save(anAccount(id = id, archivedAt = anInstant("2026-01-01T00:00:00Z")))
        val service = ArchiveAccountService(repository, aClock())

        // WHEN / THEN
        assertThatThrownBySuspending { service.archive(id) }
            .isInstanceOf(AccountAlreadyArchivedException::class.java)
    }

    @Test
    fun `does not change the account when it is already archived`() = runTest()
    {
        // GIVEN
        val id = anAccountId()
        val account = anAccount(id = id, archivedAt = anInstant("2026-01-01T00:00:00Z"))
        val repository = InMemoryAccountRepository()
        repository.save(account)
        val service = ArchiveAccountService(repository, aClock(anInstant("2026-09-23T09:00:00Z")))

        // WHEN
        assertThatThrownBySuspending { service.archive(id) }
            .isInstanceOf(AccountAlreadyArchivedException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(account)
    }
}
