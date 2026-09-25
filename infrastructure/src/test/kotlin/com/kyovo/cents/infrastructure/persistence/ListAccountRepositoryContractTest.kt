package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.port.output.AccountRepository

/** The in-memory list the app started with must keep honouring the contract. */
class ListAccountRepositoryContractTest : AccountRepositoryContract()
{
    override fun createRepository(): AccountRepository = ListAccountRepository()
}
