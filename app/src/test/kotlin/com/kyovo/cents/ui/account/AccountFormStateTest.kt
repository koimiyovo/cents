package com.kyovo.cents.ui.account

import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private fun errorsOf(submission: AccountFormSubmission): Set<AccountFormError> =
    (submission as AccountFormSubmission.Invalid).errors

private fun commandOf(form: AccountFormState): OpenAccountCommand =
    (form.submit() as AccountFormSubmission.Open).command

class AccountFormStateTest
{
    @Test
    fun `a valid form becomes an open-account command in euros`()
    {
        // GIVEN
        val form = AccountFormState(
            name = "Livret A",
            type = AccountType.SAVINGS,
            initialAmountText = "1250,50",
            description = "Épargne de précaution",
        )

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            AccountFormSubmission.Open(
                OpenAccountCommand(
                    name = AccountName("Livret A"),
                    type = AccountType.SAVINGS,
                    currency = DEFAULT_ACCOUNT_CURRENCY,
                    initialAmount = Money(125_050),
                    description = AccountDescription.of("Épargne de précaution"),
                ),
            ),
        )
    }

    @Test
    fun `only a name is needed - the account starts empty, checking, without description`()
    {
        // WHEN
        val command = commandOf(AccountFormState(name = "Espèces"))

        // THEN
        assertThat(command.type).isEqualTo(AccountType.CHECKING)
        assertThat(command.initialAmount).isEqualTo(Money(0))
        assertThat(command.description).isNull()
    }

    @Test
    fun `the name is trimmed and a blank description becomes null`()
    {
        // WHEN
        val command = commandOf(AccountFormState(name = "  Espèces  ", description = "   "))

        // THEN
        assertThat(command.name).isEqualTo(AccountName("Espèces"))
        assertThat(command.description).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a blank name is rejected`(name: String)
    {
        assertThat(errorsOf(AccountFormState(name = name).submit()))
            .containsExactly(AccountFormError.NAME_REQUIRED)
    }

    // ';' as delimiter: the default ',' is also the decimal separator being tested.
    @ParameterizedTest
    @CsvSource(
        delimiter = ';',
        value = [
            "0;0",
            "0,00;0",
            "12;1200",
            "12,5;1250",
            "12,;1200",
        ],
    )
    fun `an explicit opening balance is parsed, zero included`(text: String, expectedCents: Long)
    {
        // WHEN
        val command = commandOf(AccountFormState(name = "A", initialAmountText = text))

        // THEN
        assertThat(command.initialAmount).isEqualTo(Money(expectedCents))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a blank opening balance means an empty account`(text: String)
    {
        assertThat(commandOf(AccountFormState(name = "A", initialAmountText = text)).initialAmount)
            .isEqualTo(Money(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "-5", "12,505", "1,2,3", "12 €"])
    fun `a malformed opening balance is rejected`(text: String)
    {
        assertThat(errorsOf(AccountFormState(name = "A", initialAmountText = text).submit()))
            .containsExactly(AccountFormError.INITIAL_AMOUNT_INVALID)
    }

    @Test
    fun `every problem is reported at once`()
    {
        assertThat(errorsOf(AccountFormState(name = "", initialAmountText = "abc").submit()))
            .containsExactlyInAnyOrder(AccountFormError.NAME_REQUIRED, AccountFormError.INITIAL_AMOUNT_INVALID)
    }
}
