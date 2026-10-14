package com.kyovo.cents.infrastructure.exchange

import java.io.IOException

/**
 * The server answered with an error status (a 500, a 404, a 429 for too many requests...). An [IOException] like
 * the other failures of the network (no connection, a timeout), so that a caller treats them all the same way:
 * try again later.
 */
class ExchangeRatesServerException(val statusCode: Int) :
    IOException("Frankfurter answered with HTTP $statusCode")
