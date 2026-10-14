package com.kyovo.cents.ui.account

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private fun openedWith(form: AccountFormState): OpenAccountCommand =
    (form.submit() as AccountFormSubmission.Open).command

class AccountFormStateCurrencyTest
{
    @Test
    fun `a new form starts in the euro unless told otherwise`()
    {
        assertThat(AccountFormState().currency).isEqualTo(AccountCurrency.EUR)
    }

    @Test
    fun `the account is opened in the currency chosen`()
    {
        val command = openedWith(AccountFormState(name = "Compte dollars", currency = AccountCurrency.USD))

        assertThat(command.currency).isEqualTo(AccountCurrency.USD)
    }

    @Test
    fun `an opening balance in dollars keeps its cents`()
    {
        val command = openedWith(
            AccountFormState(name = "Compte dollars", initialAmountText = "12,50", currency = AccountCurrency.USD),
        )

        assertThat(command.initialAmount).isEqualTo(Money(1_250))
    }

    @Test
    fun `an opening balance in CFA francs is a count of francs`()
    {
        val command = openedWith(
            AccountFormState(name = "Compte CFA", initialAmountText = "150000", currency = AccountCurrency.XOF),
        )

        assertThat(command.initialAmount).isEqualTo(Money(150_000))
    }

    @Test
    fun `a decimal opening balance is refused in CFA francs`()
    {
        val submission = AccountFormState(
            name = "Compte CFA",
            initialAmountText = "1500,50",
            currency = AccountCurrency.XOF,
        ).submit()

        assertThat((submission as AccountFormSubmission.Invalid).errors)
            .containsExactly(AccountFormError.INITIAL_AMOUNT_INVALID)
    }

    @Test
    fun `switching to the CFA franc drops the decimals already typed`()
    {
        val form = AccountFormState(initialAmountText = "12,50").withCurrency(AccountCurrency.XOF)

        assertThat(form.currency).isEqualTo(AccountCurrency.XOF)
        assertThat(form.initialAmountText).isEqualTo("12")
    }

    @Test
    fun `switching to the CFA franc keeps a whole amount as it was`()
    {
        assertThat(AccountFormState(initialAmountText = "1500").withCurrency(AccountCurrency.XOF).initialAmountText)
            .isEqualTo("1500")
    }

    @Test
    fun `a trailing separator is dropped with the decimals`()
    {
        assertThat(AccountFormState(initialAmountText = "12,").withCurrency(AccountCurrency.XOF).initialAmountText)
            .isEqualTo("12")
    }

    @Test
    fun `switching from the CFA franc to the euro keeps the amount`()
    {
        val form = AccountFormState(initialAmountText = "1500", currency = AccountCurrency.XOF)
            .withCurrency(AccountCurrency.EUR)

        assertThat(form.currency).isEqualTo(AccountCurrency.EUR)
        assertThat(form.initialAmountText).isEqualTo("1500")
    }

    @Test
    fun `switching between euro and dollar keeps the cents`()
    {
        assertThat(AccountFormState(initialAmountText = "12,50").withCurrency(AccountCurrency.USD).initialAmountText)
            .isEqualTo("12,50")
    }

    @Test
    fun `an empty opening balance stays empty when the currency changes`()
    {
        assertThat(AccountFormState().withCurrency(AccountCurrency.XOF).initialAmountText).isEmpty()
    }

    @Test
    fun `editing an account keeps its currency, and the currency cannot be changed`()
    {
        val account = Account(
            id = AccountId(UUID.randomUUID()),
            name = AccountName("Compte CFA"),
            type = AccountType.CHECKING,
            currency = AccountCurrency.XOF,
            createdAt = Instant.parse("2026-09-01T10:00:00Z"),
            archivedAt = null,
        )

        val form = AccountFormState.editing(account)

        assertThat(form.currency).isEqualTo(AccountCurrency.XOF)
        assertThat(form.withCurrency(AccountCurrency.EUR).currency).isEqualTo(AccountCurrency.XOF)
    }
}
