package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class TransactionTest
{
    private val id = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
    private val amount = Money(1_000)
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    @Test
    fun `an opening deposit is always of type INITIAL_DEPOSIT`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.type).isEqualTo(TransactionType.INITIAL_DEPOSIT)
    }

    @Test
    fun `a recorded expense is of type EXPENSE`()
    {
        // WHEN
        val transaction = Transaction.recorded(id, accountId, amount, RecordableTransactionType.EXPENSE, date)

        // THEN
        assertThat(transaction.type).isEqualTo(TransactionType.EXPENSE)
    }

    @Test
    fun `a recorded income is of type INCOME`()
    {
        // WHEN
        val transaction = Transaction.recorded(id, accountId, amount, RecordableTransactionType.INCOME, date)

        // THEN
        assertThat(transaction.type).isEqualTo(TransactionType.INCOME)
    }
}
