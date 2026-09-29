package com.kyovo.cents.domain.port.input

/**
 * Catches every recurring transaction up: creates the real transactions of the occurrences that have already
 * come (even from past months, for a rule left alone for a while), and at most one more, the next one, when it
 * falls later in the current month. Never further ahead: generating months in advance filled the transactions
 * list with what was not due yet. Meant to be called whenever the app is opened and once a day; safe to call
 * again any time, since an occurrence already generated is never redone, nor is a second one pulled ahead
 * while one already is.
 *
 * When it records expenses dated in the current month, it also checks that month's budget alerts at once
 * (see [NotifyBudgetAlertUseCase]): the spending arrives without the user typing it, so the user is told
 * right away that a budget just reached its threshold or its limit.
 */
interface GenerateRecurringTransactionsUseCase
{
    suspend fun generate()
}
