package com.kyovo.cents.ui.transaction

import org.junit.jupiter.api.extension.ExtendWith
import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.InvalidInitialDepositAmountException
import com.kyovo.cents.domain.exception.NotAnInitialDepositException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.UpdateInitialDepositUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

private val OPENED = Instant.parse("2026-01-01T00:00:00Z")

/** Records the (id, amount) pairs it is asked to save; can be told to fail instead. */
private class FakeUpdateInitialDeposit : UpdateInitialDepositUseCase
{
    val calls = mutableListOf<Pair<TransactionId, Money>>()
    var failWith: RuntimeException? = null

    override suspend fun update(id: TransactionId, amount: Money): Transaction
    {
        failWith?.let { throw it }
        calls += id to amount
        return Transaction.openingDeposit(id, AccountId(Uuid.random()), amount, OPENED)
    }
}

private fun aDeposit(cents: Long = 10_000) =
    Transaction.openingDeposit(TransactionId(Uuid.random()), AccountId(Uuid.random()), Money(cents), OPENED)

@ExtendWith(MainDispatcherExtension::class)
class InitialDepositFormViewModelTest
{
    private val updateInitialDeposit = FakeUpdateInitialDeposit()
    private val revision = DataRevision()
    private val viewModel = InitialDepositFormViewModel(updateInitialDeposit, revision)

    private val state get() = viewModel.uiState.value
    private val form get() = state.form

    @Test
    fun `is closed until a deposit is opened`()
    {
        assertThat(form).isNull()
    }

    @Test
    fun `opening a deposit shows its amount, without errors`()
    {
        // GIVEN
        val deposit = aDeposit(cents = 25_050)

        // WHEN
        viewModel.openForEdit(deposit)

        // THEN
        assertThat(form).isEqualTo(InitialDepositFormState.editing(deposit))
        assertThat(state.showErrors).isFalse()
        assertThat(state.failure).isNull()
    }

    @Test
    fun `opening something that is not an opening deposit does nothing`()
    {
        // GIVEN
        val expense = Transaction.recorded(
            TransactionId(Uuid.random()), AccountId(Uuid.random()), Money(100), TransactionTitle("Courses"),
            RecordableTransactionCategory.EXPENSE, null, null, OPENED,
        )

        // WHEN
        viewModel.openForEdit(expense)

        // THEN
        assertThat(form).isNull()
    }

    @Test
    fun `close discards the form`()
    {
        // GIVEN
        viewModel.openForEdit(aDeposit())

        // WHEN
        viewModel.close()

        // THEN
        assertThat(state).isEqualTo(InitialDepositUiState())
    }

    @Test
    fun `a valid amount is saved, the lists are told to refresh, and the sheet closes`()
    {
        // GIVEN
        val deposit = aDeposit(cents = 10_000)
        viewModel.openForEdit(deposit)
        viewModel.update(form!!.copy(amountText = "300,50"))
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(updateInitialDeposit.calls).containsExactly(deposit.id to Money(30_050))
        assertThat(revision.value.value).isEqualTo(revisionBefore + 1)
        assertThat(form).isNull()
    }

    @Test
    fun `an invalid amount shows the errors and saves nothing`()
    {
        // GIVEN
        viewModel.openForEdit(aDeposit())
        viewModel.update(form!!.copy(amountText = "0"))
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.showErrors).isTrue()
        assertThat(form).isNotNull()
        assertThat(updateInitialDeposit.calls).isEmpty()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `the deposit disappearing or changing kind is an answer on screen, not a crash`()
    {
        listOf(TransactionNotFoundException(), NotAnInitialDepositException()).forEach { refusal ->
            // GIVEN
            viewModel.openForEdit(aDeposit())
            updateInitialDeposit.failWith = refusal
            val revisionBefore = revision.value.value

            // WHEN
            viewModel.submit()

            // THEN the form stays open with the reason, and nothing is refreshed
            assertThat(state.failure).isEqualTo(InitialDepositFailure.DEPOSIT_UNAVAILABLE)
            assertThat(form).isNotNull()
            assertThat(revision.value.value).isEqualTo(revisionBefore)
            viewModel.close()
        }
    }

    @Test
    fun `a zero the form let through is still answered by showing the amount error`()
    {
        // GIVEN the domain refuses a 0 the form should never have produced
        viewModel.openForEdit(aDeposit())
        updateInitialDeposit.failWith = InvalidInitialDepositAmountException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.showErrors).isTrue()
        assertThat(form).isNotNull()
    }

    @Test
    fun `editing the form after a failure clears the failure`()
    {
        // GIVEN
        viewModel.openForEdit(aDeposit())
        updateInitialDeposit.failWith = TransactionNotFoundException()
        viewModel.submit()

        // WHEN
        viewModel.update(form!!.copy(amountText = "50"))

        // THEN
        assertThat(state.failure).isNull()
    }
}
