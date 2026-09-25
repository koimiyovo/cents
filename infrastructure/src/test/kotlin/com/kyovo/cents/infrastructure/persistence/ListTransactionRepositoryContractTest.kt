package com.kyovo.cents.infrastructure.persistence

/** The in-memory lists the app started with must keep honouring the contract. */
class ListTransactionRepositoryContractTest : TransactionRepositoryContract()
{
    override fun createStores() = Stores(ListAccountRepository(), ListSubcategoryRepository(), ListTransactionRepository())
}
