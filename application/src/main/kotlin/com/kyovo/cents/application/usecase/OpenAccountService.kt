package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.output.AccountIdGenerator
import com.kyovo.cents.domain.port.output.AccountRepository
import java.time.Clock

class OpenAccountService(
    private val accountRepository: AccountRepository,
    private val accountIdGenerator: AccountIdGenerator,
    private val clock: Clock
) : OpenAccountUseCase
{
    override fun open(command: OpenAccountCommand): Account
    {
        val account = command.toAccount(accountIdGenerator.generate(), clock.instant())
        accountRepository.save(account)
        return account
    }
}