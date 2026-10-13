package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.ExchangeRates

/**
 * Where the latest exchange rates come from (a remote service). It fails with whatever its transport
 * throws when the rates cannot be had (no network, a server error): the caller decides what to do about it.
 */
interface ExchangeRateProvider
{
    suspend fun fetchLatest(): ExchangeRates
}
