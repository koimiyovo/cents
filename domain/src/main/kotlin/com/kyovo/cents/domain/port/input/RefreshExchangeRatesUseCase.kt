package com.kyovo.cents.domain.port.input

interface RefreshExchangeRatesUseCase
{
    /** Fetches the latest rates and keeps them. If they cannot be fetched, the rates already kept stay. */
    suspend fun refresh()
}
