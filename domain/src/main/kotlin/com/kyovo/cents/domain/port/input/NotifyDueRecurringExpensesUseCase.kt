package com.kyovo.cents.domain.port.input

import java.time.LocalDate

interface NotifyDueRecurringExpensesUseCase
{
    /** Notifies each recurring expense that has an occurrence on [today] and was actually recorded (see the service). */
    suspend fun notify(today: LocalDate)
}
