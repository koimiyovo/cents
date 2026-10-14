package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val NOW = Instant.parse("2026-09-23T12:00:00Z")

private fun anAccountIn(currency: AccountCurrency) = Account(
    id = AccountId(UUID.randomUUID()),
    name = AccountName("Compte ${currency.value.currencyCode} ${UUID.randomUUID()}"),
    type = AccountType.CHECKING,
    currency = currency,
    createdAt = NOW,
    archivedAt = null,
)

private fun anExpense(accountId: AccountId?, amountText: String) = TransactionFormState(
    type = TransactionFormType.EXPENSE,
    accountId = accountId,
    amountText = amountText,
    title = "Courses",
    date = NOW,
)

class TransactionFormCurrencyTest
{
    private val euros = anAccountIn(AccountCurrency.EUR)
    private val dollars = anAccountIn(AccountCurrency.USD)
    private val francs = anAccountIn(AccountCurrency.XOF)
    private val otherFrancs = anAccountIn(AccountCurrency.XOF)
    private val everyAccount = listOf(euros, dollars, francs, otherFrancs)

    @Test
    fun `an amount in CFA francs is a count of francs`()
    {
        val submission = anExpense(francs.id, "1500").submit(fractionDigits = 0)

        val command = (submission as FormSubmission.Record).command
        assertThat(command.amount).isEqualTo(Money(1_500))
    }

    @Test
    fun `an amount in dollars keeps its cents`()
    {
        val command = (anExpense(dollars.id, "12,50").submit(fractionDigits = 2) as FormSubmission.Record).command

        assertThat(command.amount).isEqualTo(Money(1_250))
    }

    @Test
    fun `a decimal amount is refused in CFA francs`()
    {
        val submission = anExpense(francs.id, "12,50").submit(fractionDigits = 0)

        assertThat((submission as FormSubmission.Invalid).errors).contains(FormError.AMOUNT_INVALID)
    }

    @Test
    fun `without saying anything the amount is read with two decimals, like before`()
    {
        val command = (anExpense(euros.id, "12,50").submit() as FormSubmission.Record).command

        assertThat(command.amount).isEqualTo(Money(1_250))
    }

    @Test
    fun `editing shows the amount with the account's decimals`()
    {
        val francExpense = com.kyovo.cents.domain.model.Transaction.recorded(
            id = com.kyovo.cents.domain.model.TransactionId(UUID.randomUUID()),
            accountId = francs.id,
            amount = Money(1_500),
            title = com.kyovo.cents.domain.model.TransactionTitle("Marché"),
            category = com.kyovo.cents.domain.model.RecordableTransactionCategory.EXPENSE,
            subcategory = null,
            description = null,
            date = NOW,
        )

        assertThat(TransactionFormState.editing(francExpense, null, null, fractionDigits = 0).amountText)
            .isEqualTo("1500")
        assertThat(TransactionFormState.editing(francExpense, null, null).amountText).isEqualTo("15,00")
    }

    @Test
    fun `with no source chosen yet, every account is a possible destination`()
    {
        val form = TransactionFormState(type = TransactionFormType.TRANSFER, accountId = null, date = NOW)

        assertThat(form.destinationChoices(everyAccount)).containsExactlyElementsOf(everyAccount)
    }

    @Test
    fun `outside of a transfer the currency does not limit the accounts`()
    {
        val form = anExpense(euros.id, "1")

        assertThat(form.sourceChoices(everyAccount)).containsExactlyElementsOf(everyAccount)
    }
}
