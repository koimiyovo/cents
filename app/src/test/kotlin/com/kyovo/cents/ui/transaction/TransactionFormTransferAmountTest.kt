package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val NOW = Instant.parse("2026-10-14T12:00:00Z")
private val EUROS = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
private val DOLLARS = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))

private fun aTransfer(amountText: String = "100", destinationAmountText: String = "") = TransactionFormState(
    type = TransactionFormType.TRANSFER,
    accountId = EUROS,
    toAccountId = DOLLARS,
    amountText = amountText,
    destinationAmountText = destinationAmountText,
    title = "Change",
    date = NOW
)

private fun FormSubmission.transfer() = (this as FormSubmission.Transfer).command

/**
 * A transfer between two currencies: the amount sent is typed in the source's currency, and the amount received
 * may be typed too, in the destination's. Left empty, the exchange rates decide (that is the use case's business).
 */
class TransactionFormTransferAmountTest
{
    // --- Reading the amount received -------------------------------------------------------------------------

    @Test
    fun `carries the amount received that was typed, in the destination's smallest unit`()
    {
        val command = aTransfer(destinationAmountText = "109,50")
            .submit(fractionDigits = 2, destinationFractionDigits = 2).transfer()

        assertThat(command.amount).isEqualTo(Money(10_000))
        assertThat(command.destinationAmount).isEqualTo(Money(10_950))
    }

    @Test
    fun `reads the amount received with the decimals of the destination, which may differ from the source's`()
    {
        // GIVEN 100,00 EUR sent, 65 000 XOF received
        val command = aTransfer(destinationAmountText = "65000")
            .submit(fractionDigits = 2, destinationFractionDigits = 0).transfer()

        assertThat(command.destinationAmount).isEqualTo(Money(65_000))
    }

    @Test
    fun `reads the amount sent with the decimals of the source, whatever the destination is`()
    {
        // GIVEN 15 000 XOF sent, 22,50 EUR received
        val command = aTransfer(amountText = "15000", destinationAmountText = "22,50")
            .submit(fractionDigits = 0, destinationFractionDigits = 2).transfer()

        assertThat(command.amount).isEqualTo(Money(15_000))
        assertThat(command.destinationAmount).isEqualTo(Money(2_250))
    }

    @Test
    fun `leaves the amount received to the exchange rates when nothing was typed`()
    {
        val command = aTransfer(destinationAmountText = "")
            .submit(fractionDigits = 2, destinationFractionDigits = 2).transfer()

        assertThat(command.destinationAmount).isNull()
    }

    @Test
    fun `leaves it to the exchange rates when only spaces were typed`()
    {
        val command = aTransfer(destinationAmountText = "   ")
            .submit(fractionDigits = 2, destinationFractionDigits = 2).transfer()

        assertThat(command.destinationAmount).isNull()
    }

    // Within one currency the field is not shown: what is left in it is not the user's wish.
    @Test
    fun `ignores the amount received between two accounts of the same currency`()
    {
        val command = aTransfer(destinationAmountText = "109,50")
            .submit(fractionDigits = 2, destinationFractionDigits = null).transfer()

        assertThat(command.destinationAmount).isNull()
    }

    @Test
    fun `an amount received is not read as sent when the destination is not in the picture`()
    {
        // WHEN the caller says nothing about the destination (the default)
        val command = aTransfer(destinationAmountText = "109,50").submit().transfer()

        // THEN it is the same currency: no amount received of its own
        assertThat(command.destinationAmount).isNull()
    }

    // --- What cannot be read ----------------------------------------------------------------------------------

    @Test
    fun `refuses an amount received that is not an amount`()
    {
        val submission = aTransfer(destinationAmountText = "a lot")
            .submit(fractionDigits = 2, destinationFractionDigits = 2)

        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.DESTINATION_AMOUNT_INVALID)
    }

    @Test
    fun `refuses an amount received of zero, which would be nothing received`()
    {
        val submission = aTransfer(destinationAmountText = "0")
            .submit(fractionDigits = 2, destinationFractionDigits = 2)

        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.DESTINATION_AMOUNT_INVALID)
    }

    @Test
    fun `refuses decimals in an amount received in CFA francs`()
    {
        val submission = aTransfer(destinationAmountText = "65000,5")
            .submit(fractionDigits = 2, destinationFractionDigits = 0)

        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.DESTINATION_AMOUNT_INVALID)
    }

    @Test
    fun `reports the amount sent and the amount received together when both are wrong`()
    {
        val submission = aTransfer(amountText = "", destinationAmountText = "x")
            .submit(fractionDigits = 2, destinationFractionDigits = 2)

        assertThat((submission as FormSubmission.Invalid).errors)
            .containsExactlyInAnyOrder(FormError.AMOUNT_INVALID, FormError.DESTINATION_AMOUNT_INVALID)
    }

    @Test
    fun `does not check an amount received that is ignored`()
    {
        // GIVEN garbage left in the field, and the same currency on both sides
        val submission = aTransfer(destinationAmountText = "x")
            .submit(fractionDigits = 2, destinationFractionDigits = null)

        // THEN
        assertThat(submission).isInstanceOf(FormSubmission.Transfer::class.java)
    }

    // --- Typing -----------------------------------------------------------------------------------------------

    @Test
    fun `accepts typing digits and a separator in the amount received`()
    {
        assertThat(aTransfer().withDestinationAmount("109,5", fractionDigits = 2).destinationAmountText)
            .isEqualTo("109,5")
    }

    @Test
    fun `ignores an edit that would not be an amount`()
    {
        val form = aTransfer(destinationAmountText = "109")

        assertThat(form.withDestinationAmount("109a", fractionDigits = 2)).isEqualTo(form)
        assertThat(form.withDestinationAmount("109,555", fractionDigits = 2)).isEqualTo(form)
    }

    @Test
    fun `takes no separator in an amount received in CFA francs`()
    {
        val form = aTransfer(destinationAmountText = "65000")

        assertThat(form.withDestinationAmount("65000,", fractionDigits = 0)).isEqualTo(form)
    }

    @Test
    fun `forgets the amount received when the form stops being a transfer`()
    {
        val form = aTransfer(destinationAmountText = "109,50")

        assertThat(form.withType(TransactionFormType.EXPENSE).destinationAmountText).isEmpty()
    }

    @Test
    fun `keeps the amount received when the type is set to a transfer again`()
    {
        val form = aTransfer(destinationAmountText = "109,50")

        assertThat(form.withType(TransactionFormType.TRANSFER).destinationAmountText).isEqualTo("109,50")
    }

    // --- The accounts are no longer limited to one currency --------------------------------------------------

    @Test
    fun `offers accounts of every currency on the other side of a transfer`()
    {
        val accounts = listOf(
            account(EUROS, AccountCurrency.EUR), account(DOLLARS, AccountCurrency.USD),
            account(AccountId(UUID.randomUUID()), AccountCurrency.XOF)
        )
        val form = TransactionFormState(type = TransactionFormType.TRANSFER, accountId = EUROS, date = NOW)

        assertThat(form.destinationChoices(accounts).map { it.currency })
            .containsExactly(AccountCurrency.USD, AccountCurrency.XOF)
    }

    @Test
    fun `offers sources of every currency too`()
    {
        val accounts = listOf(
            account(EUROS, AccountCurrency.EUR), account(DOLLARS, AccountCurrency.USD),
            account(AccountId(UUID.randomUUID()), AccountCurrency.XOF)
        )
        val form = TransactionFormState(type = TransactionFormType.TRANSFER, accountId = null, toAccountId = DOLLARS, date = NOW)

        assertThat(form.sourceChoices(accounts).map { it.currency })
            .containsExactly(AccountCurrency.EUR, AccountCurrency.XOF)
    }

    private fun account(id: AccountId, currency: AccountCurrency) = com.kyovo.cents.domain.model.Account(
        id = id,
        name = com.kyovo.cents.domain.model.AccountName("Compte ${currency.value.currencyCode} ${UUID.randomUUID()}"),
        type = com.kyovo.cents.domain.model.AccountType.CHECKING,
        currency = currency,
        createdAt = NOW,
        archivedAt = null
    )
}

/**
 * What the screen suggests for the amount received, at the ECB's rate: shown under the field, so that the user
 * can compare it with what their bank gave before deciding whether to type their own.
 */
class TransferEstimateTest
{
    // 1 EUR = 1.10 USD
    private val rates = ExchangeRates(LocalDate.of(2026, 10, 12), mapOf(AccountCurrency.USD to BigDecimal("1.10")))

    @Test
    fun `converts the amount typed at the rates`()
    {
        assertThat(transferEstimate("100", AccountCurrency.EUR, AccountCurrency.USD, rates)).isEqualTo(Money(11_000))
    }

    @Test
    fun `reads the amount with the decimals of the source currency`()
    {
        // 15 000 XOF is 22.87 EUR, 25.16 USD
        assertThat(transferEstimate("15000", AccountCurrency.XOF, AccountCurrency.EUR, rates)).isEqualTo(Money(2_287))
    }

    @Test
    fun `has no estimate while no amount was typed`()
    {
        assertThat(transferEstimate("", AccountCurrency.EUR, AccountCurrency.USD, rates)).isNull()
    }

    @Test
    fun `has no estimate for an amount that is not valid`()
    {
        assertThat(transferEstimate("abc", AccountCurrency.EUR, AccountCurrency.USD, rates)).isNull()
        assertThat(transferEstimate("0", AccountCurrency.EUR, AccountCurrency.USD, rates)).isNull()
    }

    @Test
    fun `has no estimate between two accounts of the same currency, where nothing is converted`()
    {
        assertThat(transferEstimate("100", AccountCurrency.USD, AccountCurrency.USD, rates)).isNull()
    }

    @Test
    fun `has no estimate when no rates were fetched, except for the CFA franc which is fixed`()
    {
        assertThat(transferEstimate("100", AccountCurrency.EUR, AccountCurrency.USD, null)).isNull()
        assertThat(transferEstimate("100", AccountCurrency.EUR, AccountCurrency.XOF, null)).isEqualTo(Money(65_596))
    }

    @Test
    fun `has no estimate for a currency the rates do not cover`()
    {
        assertThat(transferEstimate("100", AccountCurrency.EUR, AccountCurrency.CAD, rates)).isNull()
    }

    @Test
    fun `has no estimate when the conversion rounds to nothing`()
    {
        // 1 XOF is worth 0.15 euro cent
        assertThat(transferEstimate("1", AccountCurrency.XOF, AccountCurrency.EUR, rates)).isNull()
    }
}
