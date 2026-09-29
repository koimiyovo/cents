package com.kyovo.cents.domain.port.input

import java.time.LocalDate

interface NotifyDueRecurringTransactionsUseCase
{
    /** Notifies each recurring transaction that has an occurrence on [today] and was actually recorded (see the service). */
    suspend fun notify(today: LocalDate)
}
