package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import kotlin.uuid.Uuid

private val DEPOSIT_ID = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333"))
private val DEPOSIT_ACCOUNT = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
private val OPENED_AT = Instant.parse("2026-01-01T00:00:00Z")

private fun anOpeningDeposit(cents: Long = 10_000) =
    Transaction.openingDeposit(DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(cents), OPENED_AT)

/**
 * The opening deposit has a form of its own, not the transaction form: the only thing that can be
 * corrected on it is its amount (title, date and category are fixed, and the account is the one
 * being opened), so a sheet with a single field says exactly what can be done.
 */
class CanEditInitialDepositTest
{
    @Test
    fun `an opening deposit can be edited`()
    {
        assertThat(canEditInitialDeposit(anOpeningDeposit())).isTrue()
    }

    @Test
    fun `an income, an expense and the legs of a transfer cannot`()
    {
        val title = TransactionTitle("Test")
        val others = listOf(
            Transaction.recorded(
                DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(100), title, RecordableTransactionCategory.INCOME, null, null, OPENED_AT,
            ),
            Transaction.recorded(
                DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(100), title, RecordableTransactionCategory.EXPENSE, null, null, OPENED_AT,
            ),
            Transaction.transferOut(DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(100), title, OPENED_AT),
            Transaction.transferIn(DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(100), title, OPENED_AT),
        )

        assertThat(others.map(::canEditInitialDeposit)).containsOnly(false)
    }

    @Test
    fun `the two kinds of editing never overlap`()
    {
        // A transaction opens the full form or the deposit form, never both and never neither of
        // the editable ones: a tap on a row has exactly one place to go (or none, for a transfer).
        val deposit = anOpeningDeposit()

        assertThat(canEditTransaction(deposit)).isFalse()
        assertThat(canEditInitialDeposit(deposit)).isTrue()
    }
}

class InitialDepositFormStateTest
{
    @Test
    fun `editing pre-fills the amount from the deposit`()
    {
        // WHEN
        val form = InitialDepositFormState.editing(anOpeningDeposit(cents = 25_050))

        // THEN
        assertThat(form.amountText).isEqualTo("250,50")
        assertThat(form.editingId).isEqualTo(DEPOSIT_ID)
    }

    @Test
    fun `editing something that is not an opening deposit is refused`()
    {
        // GIVEN
        val expense = Transaction.recorded(
            DEPOSIT_ID, DEPOSIT_ACCOUNT, Money(100), TransactionTitle("Courses"),
            RecordableTransactionCategory.EXPENSE, null, null, OPENED_AT,
        )

        // WHEN / THEN
        assertThatThrownBy { InitialDepositFormState.editing(expense) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a valid amount submits an update of that deposit`()
    {
        // GIVEN
        val form = InitialDepositFormState.editing(anOpeningDeposit()).copy(amountText = "300,5")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(InitialDepositSubmission.Update(DEPOSIT_ID, Money(30_050)))
    }

    @Test
    fun `an untouched form submits the amount it was opened with`()
    {
        // WHEN
        val submission = InitialDepositFormState.editing(anOpeningDeposit(cents = 10_000)).submit()

        // THEN
        assertThat(submission).isEqualTo(InitialDepositSubmission.Update(DEPOSIT_ID, Money(10_000)))
    }

    // Zero is refused (the domain refuses it too: opening an account with 0 creates no deposit at
    // all), and so is anything that isn't a positive amount with at most two decimals.
    @ParameterizedTest
    @ValueSource(strings = ["", "  ", "0", "0,00", "abc", "12,345", "-5", "1,2,3"])
    fun `an amount that is not a positive sum is invalid`(text: String)
    {
        // GIVEN
        val form = InitialDepositFormState.editing(anOpeningDeposit()).copy(amountText = text)

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(InitialDepositSubmission.Invalid)
    }
}
