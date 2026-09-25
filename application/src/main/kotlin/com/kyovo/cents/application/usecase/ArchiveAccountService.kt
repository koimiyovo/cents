package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.exception.AccountAlreadyArchivedException
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.ArchiveAccountUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import java.time.Clock

class ArchiveAccountService(
    private val accountRepository: AccountRepository,
    private val clock: Clock
) : ArchiveAccountUseCase
{
    override suspend fun archive(id: AccountId): Account
    {
        val account = accountRepository.findById(id) ?: throw AccountNotFoundException()
        if (account.archivedAt != null)
        {
            throw AccountAlreadyArchivedException()
        }
        val archived = account.copy(archivedAt = clock.instant())
        accountRepository.save(archived)
        return archived
    }
}