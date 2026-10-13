package com.kyovo.cents.infrastructure.exchange

/**
 * What Frankfurter answered cannot be read as exchange rates (not JSON, a field missing, a rate that is not a
 * number, rates that are not against the euro...). The one exception the parser raises, whatever the JSON
 * library or the date parser throws underneath, which is kept as the cause.
 */
class MalformedExchangeRatesException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
