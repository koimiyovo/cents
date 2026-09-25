package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidRestoredTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * A storage adapter has to give a `Transaction` back from what it stored, but the constructor is
 * private and the other factories take what only the *recording* knows (a whole `Subcategory` to check
 * its kind). [Transaction.restored] takes the stored values as they are. It is not a way around the
 * rules: what was stored went through them, and a combination no factory can produce is refused.
 */
class TransactionRestoredTest
{
    private val id = TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val subcategoryId = SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
    private val date = Instant.parse("2026-09-22T10:00:00.123456789Z")
    private val title = TransactionTitle("Courses")
    private val description = TransactionDescription.of("Marché du samedi")

    private val groceries = Subcategory(subcategoryId, RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)
    private val salary = Subcategory(subcategoryId, RecordableTransactionCategory.INCOME, SubcategoryName("Salaire"), null)

    private fun restored(
        category: TransactionCategory,
        subcategoryId: SubcategoryId? = null,
        description: TransactionDescription? = null,
        title: TransactionTitle = this.title,
    ) = Transaction.restored(id, accountId, Money(1_250), title, category, subcategoryId, description, date)

    @Test
    fun `an expense is restored as the one that was recorded`()
    {
        // GIVEN
        val recorded = Transaction.recorded(id, accountId, Money(1_250), title, RecordableTransactionCategory.EXPENSE, groceries, description, date)

        // WHEN / THEN
        assertThat(restored(TransactionCategory.EXPENSE, subcategoryId, description)).isEqualTo(recorded)
    }

    @Test
    fun `an income is restored as the one that was recorded`()
    {
        // GIVEN
        val recorded = Transaction.recorded(id, accountId, Money(1_250), title, RecordableTransactionCategory.INCOME, salary, null, date)

        // WHEN / THEN
        assertThat(restored(TransactionCategory.INCOME, subcategoryId)).isEqualTo(recorded)
    }

    @Test
    fun `an expense without a subcategory or a description is restored as it was`()
    {
        // GIVEN
        val recorded = Transaction.recorded(id, accountId, Money(1_250), title, RecordableTransactionCategory.EXPENSE, null, null, date)

        // WHEN / THEN
        assertThat(restored(TransactionCategory.EXPENSE)).isEqualTo(recorded)
    }

    @Test
    fun `an opening deposit is restored as the one that was made`()
    {
        // GIVEN
        val deposit = Transaction.openingDeposit(id, accountId, Money(1_250), date)

        // WHEN / THEN
        assertThat(restored(TransactionCategory.INITIAL_DEPOSIT, title = deposit.title)).isEqualTo(deposit)
    }

    @Test
    fun `both legs of a transfer are restored as they were made`()
    {
        // GIVEN
        val out = Transaction.transferOut(id, accountId, Money(1_250), title, date)
        val `in` = Transaction.transferIn(id, accountId, Money(1_250), title, date)

        // WHEN / THEN
        assertThat(restored(TransactionCategory.TRANSFER_OUT)).isEqualTo(out)
        assertThat(restored(TransactionCategory.TRANSFER_IN)).isEqualTo(`in`)
    }

    @Test
    fun `a restored transaction counts in the balance like any other`()
    {
        assertThat(restored(TransactionCategory.EXPENSE).signedAmount).isEqualTo(-1_250)
        assertThat(restored(TransactionCategory.TRANSFER_IN).signedAmount).isEqualTo(1_250)
    }

    // Only an income or an expense can have a subcategory or a description: no factory makes anything else.
    @Test
    fun `an opening deposit with a subcategory is refused`()
    {
        assertThatThrownBy { restored(TransactionCategory.INITIAL_DEPOSIT, subcategoryId = subcategoryId) }
            .isInstanceOf(InvalidRestoredTransactionException::class.java)
    }

    @Test
    fun `an opening deposit with a description is refused`()
    {
        assertThatThrownBy { restored(TransactionCategory.INITIAL_DEPOSIT, description = description) }
            .isInstanceOf(InvalidRestoredTransactionException::class.java)
    }

    @Test
    fun `a transfer leg with a subcategory or a description is refused`()
    {
        for (category in listOf(TransactionCategory.TRANSFER_OUT, TransactionCategory.TRANSFER_IN))
        {
            assertThatThrownBy { restored(category, subcategoryId = subcategoryId) }
                .isInstanceOf(InvalidRestoredTransactionException::class.java)
            assertThatThrownBy { restored(category, description = description) }
                .isInstanceOf(InvalidRestoredTransactionException::class.java)
        }
    }
}
