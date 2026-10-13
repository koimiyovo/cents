package com.kyovo.cents.domain.exception

/** There is no rate to convert between these two currencies (one of them is not in the rates). */
class ExchangeRateUnavailableException : IllegalStateException("No exchange rate is available for this conversion")
