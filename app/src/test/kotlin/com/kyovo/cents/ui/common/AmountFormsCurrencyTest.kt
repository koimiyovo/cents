package com.kyovo.cents.ui.common

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.BudgetProgress
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.ui.budget.BudgetFormState
import com.kyovo.cents.ui.budget.BudgetRow
import com.kyovo.cents.ui.budget.BudgetSubmission
import com.kyovo.cents.ui.project.ProjectFormState
import com.kyovo.cents.ui.project.ProjectSubmission
import com.kyovo.cents.ui.recurring.RecurringTransactionFormState
import com.kyovo.cents.ui.recurring.RecurringTransactionSubmission
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import com.kyovo.cents.ui.transaction.InitialDepositFormState
import com.kyovo.cents.ui.transaction.InitialDepositSubmission
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Every form that asks for an amount reads it with the decimals of its own currency: the budget and project
 * forms the main one, the deposit and recurring forms the account's.
 */
class AmountFormsCurrencyTest
{
    private val september = YearMonth.of(2026, 9)

    // --- Budget -----------------------------------------------------------------------------------------------

    @Test
    fun `a budget limit in CFA francs is a count of francs`()
    {
        val form = BudgetFormState(GROCERIES_SUBCATEGORY, september, "150000", currency = AccountCurrency.XOF)

        val command = (form.submit() as BudgetSubmission.Set).command
        assertThat(command.limit).isEqualTo(Money(150_000))
    }

    @Test
    fun `a decimal budget limit is refused in CFA francs`()
    {
        val form = BudgetFormState(GROCERIES_SUBCATEGORY, september, "1500,50", currency = AccountCurrency.XOF)

        assertThat(form.submit()).isEqualTo(BudgetSubmission.Invalid)
    }

    @Test
    fun `typing a separator is ignored in the budget form in CFA francs`()
    {
        val form = BudgetFormState(GROCERIES_SUBCATEGORY, september, "1500", currency = AccountCurrency.XOF)

        assertThat(form.withLimit("1500,").limitText).isEqualTo("1500")
    }

    @Test
    fun `the limit in force is pre-filled with the currency's decimals`()
    {
        val row = BudgetRow(GROCERIES_SUBCATEGORY, BudgetProgress(Money(150_000), Money(0), AlertThreshold(80)))

        assertThat(BudgetFormState.setting(row, september, AccountCurrency.XOF).limitText).isEqualTo("150000")
        assertThat(BudgetFormState.setting(row, september, AccountCurrency.USD).limitText).isEqualTo("1500,00")
    }

    // --- Project ----------------------------------------------------------------------------------------------

    @Test
    fun `a project target in CFA francs is a count of francs`()
    {
        val form = ProjectFormState(name = "Voyage", targetText = "2500000", currency = AccountCurrency.XOF)

        val command = (form.submit() as ProjectSubmission.Create).command
        assertThat(command.target).isEqualTo(Money(2_500_000))
    }

    @Test
    fun `a decimal project target is refused in CFA francs`()
    {
        val form = ProjectFormState(name = "Voyage", targetText = "2500,5", currency = AccountCurrency.XOF)

        assertThat(form.submit()).isInstanceOf(ProjectSubmission.Invalid::class.java)
    }

    @Test
    fun `typing a separator is ignored in the project form in CFA francs`()
    {
        val form = ProjectFormState.creating(AccountCurrency.XOF).withTarget("12")

        assertThat(form.withTarget("12,").targetText).isEqualTo("12")
    }

    // --- Opening deposit --------------------------------------------------------------------------------------

    private fun anOpeningDeposit(cents: Long) =
        Transaction.openingDeposit(TransactionId(UUID.randomUUID()), AccountId(UUID.randomUUID()), Money(cents), Instant.parse("2026-09-01T10:00:00Z"))

    @Test
    fun `the opening deposit of a CFA franc account is shown and read as a whole number`()
    {
        val form = InitialDepositFormState.editing(anOpeningDeposit(150_000), AccountCurrency.XOF)

        assertThat(form.amountText).isEqualTo("150000")
        assertThat((form.copy(amountText = "200000").submit() as InitialDepositSubmission.Update).amount)
            .isEqualTo(Money(200_000))
    }

    @Test
    fun `a decimal opening deposit is refused in CFA francs`()
    {
        val form = InitialDepositFormState.editing(anOpeningDeposit(150_000), AccountCurrency.XOF)

        assertThat(form.copy(amountText = "1500,5").submit()).isEqualTo(InitialDepositSubmission.Invalid)
    }

    // --- Recurring transaction --------------------------------------------------------------------------------

    private val account = AccountId(UUID.randomUUID())
    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun `a recurring amount in CFA francs is a count of francs`()
    {
        val form = RecurringTransactionFormState
            .creating(account, today, AccountCurrency.XOF)
            .withTitle("Loyer")
            .withAmount("150000")

        val command = (form.submit() as RecurringTransactionSubmission.Create).command
        assertThat(command.amount).isEqualTo(Money(150_000))
    }

    @Test
    fun `choosing another account moves the form to that account's currency`()
    {
        val form = RecurringTransactionFormState.creating(account, today).withAccount(AccountId(UUID.randomUUID()), AccountCurrency.XOF)

        assertThat(form.currency).isEqualTo(AccountCurrency.XOF)
    }

    @Test
    fun `choosing another account without a currency keeps the current one`()
    {
        val form = RecurringTransactionFormState.creating(account, today, AccountCurrency.USD).withAccount(AccountId(UUID.randomUUID()))

        assertThat(form.currency).isEqualTo(AccountCurrency.USD)
    }
}
