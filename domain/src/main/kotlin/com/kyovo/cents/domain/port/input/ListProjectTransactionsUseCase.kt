package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The transactions filed under a project, every amount in the project's own currency: what its analysis (where the
 * money went, the biggest expenses) adds up and shows. Empty for a project that does not exist.
 */
interface ListProjectTransactionsUseCase
{
    fun observe(id: ProjectId): Flow<List<Transaction>>
}
