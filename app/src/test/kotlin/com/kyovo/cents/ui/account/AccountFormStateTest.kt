package com.kyovo.cents.ui.account

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.domain.port.input.UpdateAccountCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

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

private fun anAccountToEdit(description: String? = "Épargne de précaution") = Account(
    id = AccountId(Uuid.random()),
    name = AccountName("Livret A"),
    type = AccountType.SAVINGS,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    description = AccountDescription.of(description),
)

class AccountFormStateEditTest
{
    @Test
    fun `editing an account pre-fills its name, type and description`()
    {
        // GIVEN
        val account = anAccountToEdit()

        // WHEN
        val form = AccountFormState.editing(account)

        // THEN
        assertThat(form.name).isEqualTo("Livret A")
        assertThat(form.type).isEqualTo(AccountType.SAVINGS)
        assertThat(form.description).isEqualTo("Épargne de précaution")
        assertThat(form.editingId).isEqualTo(account.id)
        assertThat(form.isEditing).isTrue()
    }

    @Test
    fun `an account without a description gives an empty description field`()
    {
        // WHEN
        val form = AccountFormState.editing(anAccountToEdit(description = null))

        // THEN
        assertThat(form.description).isEmpty()
    }

    @Test
    fun `a new-account form is not an edit`()
    {
        assertThat(AccountFormState().isEditing).isFalse()
    }

    @Test
    fun `an edited form becomes an update command carrying the account's id`()
    {
        // GIVEN
        val account = anAccountToEdit()
        val form = AccountFormState.editing(account)
            .copy(name = "  Livret B ", type = AccountType.CHECKING, description = "Nouvelle description")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            AccountFormSubmission.Update(
                UpdateAccountCommand(
                    id = account.id,
                    name = AccountName("Livret B"),
                    type = AccountType.CHECKING,
                    description = AccountDescription.of("Nouvelle description"),
                ),
            ),
        )
    }

    @Test
    fun `emptying the description removes it`()
    {
        // GIVEN
        val form = AccountFormState.editing(anAccountToEdit()).copy(description = "   ")

        // WHEN
        val command = (form.submit() as AccountFormSubmission.Update).command

        // THEN
        assertThat(command.description).isNull()
    }

    @Test
    fun `an edit ignores the opening balance field`()
    {
        // GIVEN a leftover, malformed balance text must not block an edit: the field isn't shown
        val form = AccountFormState.editing(anAccountToEdit()).copy(initialAmountText = "abc")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isInstanceOf(AccountFormSubmission.Update::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a blank name is rejected when editing`(name: String)
    {
        // GIVEN
        val form = AccountFormState.editing(anAccountToEdit()).copy(name = name)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(errorsOf(submission)).containsExactly(AccountFormError.NAME_REQUIRED)
    }
}
