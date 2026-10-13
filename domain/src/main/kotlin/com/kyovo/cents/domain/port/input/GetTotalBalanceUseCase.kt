package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.TotalBalance
import kotlinx.coroutines.flow.Flow

interface GetTotalBalanceUseCase
{
    /** The consolidated balance of the active accounts in the main currency; it follows every change. */
    fun observe(): Flow<TotalBalance>
}
