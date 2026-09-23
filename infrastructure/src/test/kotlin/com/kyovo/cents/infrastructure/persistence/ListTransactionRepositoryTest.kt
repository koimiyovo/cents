package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class ListTransactionRepositoryTest
{
    private val id = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    @Test
    fun `finds a transaction that has been saved`()
    {
        // GIVEN
        val repository = ListTransactionRepository()
        val transaction = Transaction.openingDeposit(id, accountId, Money(1_000), date)
        repository.save(transaction)

        // WHEN / THEN
        assertThat(repository.findById(id)).isEqualTo(transaction)
    }

    @Test
    fun `replaces an existing transaction when saving another transaction with the same id`()
    {
        // GIVEN
        val repository = ListTransactionRepository()
        repository.save(Transaction.openingDeposit(id, accountId, Money(1_000), date))

        // WHEN
        val updated = Transaction.openingDeposit(id, accountId, Money(5_000), date)
        repository.save(updated)

        // THEN
        assertThat(repository.findById(id)).isEqualTo(updated)
        assertThat(repository.snapshot()).hasSize(1)
    }

    @Test
    fun `no longer finds a transaction once it has been deleted`()
    {
        // GIVEN
        val repository = ListTransactionRepository()
        repository.save(Transaction.openingDeposit(id, accountId, Money(1_000), date))

        // WHEN
        repository.deleteById(id)

        // THEN
        assertThat(repository.findById(id)).isNull()
    }

    @Test
    fun `does not throw when deleting a transaction that does not exist`()
    {
        // GIVEN
        val repository = ListTransactionRepository()

        // WHEN / THEN
        assertThatCode { repository.deleteById(id) }.doesNotThrowAnyException()
    }
}
