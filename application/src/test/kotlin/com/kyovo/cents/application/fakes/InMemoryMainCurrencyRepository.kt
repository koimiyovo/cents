package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The main currency kept in memory; null until one is chosen, like the real one. */
class InMemoryMainCurrencyRepository(initial: AccountCurrency? = null) : MainCurrencyRepository
{
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AccountCurrency?> = state

    override suspend fun save(currency: AccountCurrency)
    {
        state.value = currency
    }
}
