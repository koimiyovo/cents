package com.kyovo.cents.domain.port.input

/**
 * Catches every recurring expense up: creates the real transactions for whichever of their due months
 * have not been generated yet, up to a bounded horizon ahead of today (never forever — an endless rule
 * cannot generate endlessly). Meant to be called once whenever the app is opened; safe to call again any
 * time, since a month already generated is never redone.
 */
interface GenerateRecurringExpensesUseCase
{
    suspend fun generate()
}
