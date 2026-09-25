package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.port.output.SubcategoryRepository

/** The in-memory list the app started with must keep honouring the contract. */
class ListSubcategoryRepositoryContractTest : SubcategoryRepositoryContract()
{
    override fun createRepository(): SubcategoryRepository = ListSubcategoryRepository()
}
