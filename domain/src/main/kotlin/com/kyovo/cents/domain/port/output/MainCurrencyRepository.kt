package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.AccountCurrency
import kotlinx.coroutines.flow.Flow

/**
 * The currency the user's totals are shown in (the "main" one). Null until the user has chosen it, which
 * the app asks for at its first launch. Without exchange rates yet, the totals only count the accounts
 * held in that currency.
 */
interface MainCurrencyRepository
{
    fun observe(): Flow<AccountCurrency?>

    suspend fun save(currency: AccountCurrency)
}
