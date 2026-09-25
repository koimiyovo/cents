package com.kyovo.cents.infrastructure.persistence

/** The unit of work of the in-memory lists (snapshot, then restore on failure) must honour the contract. */
class ListUnitOfWorkContractTest : UnitOfWorkContract()
{
    override fun createStorage(): Storage
    {
        val accounts = ListAccountRepository()
        val transactions = ListTransactionRepository()
        val subcategories = ListSubcategoryRepository()
        return Storage(Stores(accounts, subcategories, transactions), ListUnitOfWork(accounts, transactions, subcategories))
    }
}
