package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.Currency
import kotlin.uuid.Uuid

private val NOW = Instant.parse("2026-09-23T12:00:00Z")
private val CHECKING = AccountId(Uuid.random())
private val SAVINGS = AccountId(Uuid.random())

private fun anExpenseForm(
    accountId: AccountId? = CHECKING,
    amountText: String = "12,50",
    title: String = "Courses",
) = TransactionFormState(
    type = TransactionFormType.EXPENSE,
    accountId = accountId,
    amountText = amountText,
    title = title,
    date = NOW,
)

private fun aTransferForm(
    from: AccountId? = CHECKING,
    to: AccountId? = SAVINGS,
    amountText: String = "50",
    title: String = "Épargne du mois",
) = TransactionFormState(
    type = TransactionFormType.TRANSFER,
    accountId = from,
    toAccountId = to,
    amountText = amountText,
    title = title,
    date = NOW,
)

private fun anAccount(id: AccountId, archived: Boolean = false) = Account(
    id = id,
    name = AccountName("Account ${id.value}"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = NOW,
    archivedAt = if (archived) NOW else null,
)

private fun FormSubmission.invalidErrors(): Set<FormError> = (this as FormSubmission.Invalid).errors

class TransactionFormSubmitTest
{
    @Test
    fun `a valid expense becomes a record command`()
    {
        // GIVEN
        val form = anExpenseForm().copy(
            subcategory = GROCERIES_SUBCATEGORY,
            description = "Marché du samedi",
        )

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            FormSubmission.Record(
                RecordTransactionCommand(
                    accountId = CHECKING,
                    amount = Money(1250),
                    title = TransactionTitle("Courses"),
                    category = RecordableTransactionCategory.EXPENSE,
                    subcategoryId = GROCERIES_SUBCATEGORY.id,
                    description = TransactionDescription.of("Marché du samedi"),
                    date = NOW,
                ),
            ),
        )
    }

    @Test
    fun `a valid income becomes a record command with the income category`()
    {
        // GIVEN
        val form = anExpenseForm().copy(
            type = TransactionFormType.INCOME,
            subcategory = SALARY_SUBCATEGORY,
        )

        // WHEN
        val submission = form.submit() as FormSubmission.Record

        // THEN
        assertThat(submission.command.category).isEqualTo(RecordableTransactionCategory.INCOME)
        assertThat(submission.command.subcategoryId).isEqualTo(SALARY_SUBCATEGORY.id)
    }

    @Test
    fun `title and description are trimmed and a blank description becomes null`()
    {
        // GIVEN
        val form = anExpenseForm(title = "  Courses  ").copy(description = "   ")

        // WHEN
        val command = (form.submit() as FormSubmission.Record).command

        // THEN
        assertThat(command.title).isEqualTo(TransactionTitle("Courses"))
        assertThat(command.description).isNull()
    }

    @Test
    fun `a valid transfer becomes a transfer command`()
    {
        // WHEN
        val submission = aTransferForm().submit()

        // THEN
        assertThat(submission).isEqualTo(
            FormSubmission.Transfer(
                RecordTransferCommand(
                    fromAccountId = CHECKING,
                    toAccountId = SAVINGS,
                    amount = Money(5000),
                    title = TransactionTitle("Épargne du mois"),
                    date = NOW,
                ),
            ),
        )
    }

    @Test
    fun `a blank title is rejected`()
    {
        // WHEN
        val submission = anExpenseForm(title = "   ").submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.TITLE_REQUIRED)
    }

    @Test
    fun `a missing account is rejected`()
    {
        // WHEN
        val submission = anExpenseForm(accountId = null).submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.ACCOUNT_REQUIRED)
    }

    @Test
    fun `every problem is reported at once, not just the first`()
    {
        // WHEN
        val submission = anExpenseForm(accountId = null, amountText = "abc", title = "").submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactlyInAnyOrder(
            FormError.ACCOUNT_REQUIRED,
            FormError.AMOUNT_INVALID,
            FormError.TITLE_REQUIRED,
        )
    }

    @Test
    fun `a subcategory that does not belong to the category is rejected`()
    {
        // GIVEN
        val form = anExpenseForm().copy(subcategory = SALARY_SUBCATEGORY)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.SUBCATEGORY_MISMATCH)
    }

    @Test
    fun `a transfer needs a destination account`()
    {
        // WHEN
        val submission = aTransferForm(to = null).submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.DESTINATION_ACCOUNT_REQUIRED)
    }

    @Test
    fun `a transfer to the same account is rejected`()
    {
        // WHEN
        val submission = aTransferForm(from = CHECKING, to = CHECKING).submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.SAME_ACCOUNT)
    }

    @Test
    fun `an expense does not require a destination account`()
    {
        // WHEN
        val submission = anExpenseForm().copy(toAccountId = null).submit()

        // THEN
        assertThat(submission).isInstanceOf(FormSubmission.Record::class.java)
    }
}

/**
 * The domain's Money accepts 0, but a zero-amount transaction is never what the user meant, so the
 * form is stricter than the domain here. It also refuses to silently round: Money is whole cents,
 * so a third decimal is an input mistake to surface, not to truncate.
 */
class TransactionFormAmountTest
{
    @ParameterizedTest
    // ';' as delimiter: the default ',' is also the decimal separator being tested.
    @CsvSource(
        delimiter = ';',
        value = [
            "12,50;1250",
            "12.50;1250",
            "12;1200",
            "12,;1200",
            "0,99;99",
            "0.5;50",
            "1250;125000",
            "' 7,5 ';750",
        ],
    )
    fun `parses an amount typed in euros to cents`(text: String, expectedCents: Long)
    {
        // WHEN
        val command = (anExpenseForm(amountText = text).submit() as FormSubmission.Record).command

        // THEN
        assertThat(command.amount).isEqualTo(Money(expectedCents))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "abc", "0", "0,00", "-5", "12,505", "1,2,3", "12 €"])
    fun `rejects an amount that is empty, non numeric, zero, negative or finer than a cent`(text: String)
    {
        // WHEN
        val submission = anExpenseForm(amountText = text).submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.AMOUNT_INVALID)
    }

    @Test
    fun `an invalid amount is also rejected on a transfer`()
    {
        // WHEN
        val submission = aTransferForm(amountText = "0").submit()

        // THEN
        assertThat(submission.invalidErrors()).containsExactly(FormError.AMOUNT_INVALID)
    }
}

class TransactionFormTypeSwitchTest
{
    @Test
    fun `switching from expense to income drops an expense subcategory`()
    {
        // GIVEN
        val form = anExpenseForm().copy(subcategory = GROCERIES_SUBCATEGORY)

        // WHEN
        val switched = form.withType(TransactionFormType.INCOME)

        // THEN
        assertThat(switched.type).isEqualTo(TransactionFormType.INCOME)
        assertThat(switched.subcategory).isNull()
    }

    @Test
    fun `switching to transfer drops the subcategory`()
    {
        // GIVEN
        val form = anExpenseForm().copy(subcategory = FUEL_SUBCATEGORY)

        // WHEN
        val switched = form.withType(TransactionFormType.TRANSFER)

        // THEN
        assertThat(switched.subcategory).isNull()
    }

    @Test
    fun `switching type keeps what the user already typed`()
    {
        // GIVEN
        val form = anExpenseForm(amountText = "42", title = "Essence").copy(description = "Plein")

        // WHEN
        val switched = form.withType(TransactionFormType.INCOME)

        // THEN
        assertThat(switched.amountText).isEqualTo("42")
        assertThat(switched.title).isEqualTo("Essence")
        assertThat(switched.description).isEqualTo("Plein")
        assertThat(switched.accountId).isEqualTo(CHECKING)
    }

    @Test
    fun `a subcategory that still fits the new type is kept`()
    {
        // GIVEN
        val form = anExpenseForm().copy(subcategory = GROCERIES_SUBCATEGORY)

        // WHEN
        val switched = form.withType(TransactionFormType.EXPENSE)

        // THEN
        assertThat(switched.subcategory).isEqualTo(GROCERIES_SUBCATEGORY)
    }

    @Test
    fun `switching to transfer drops a destination equal to the chosen account`()
    {
        // GIVEN a destination typed earlier that has since become the chosen account
        val form = anExpenseForm(accountId = SAVINGS).copy(toAccountId = SAVINGS)

        // WHEN
        val switched = form.withType(TransactionFormType.TRANSFER)

        // THEN
        assertThat(switched.toAccountId).isNull()
        assertThat(switched.accountId).isEqualTo(SAVINGS)
    }

    @Test
    fun `switching to transfer keeps a destination different from the chosen account`()
    {
        // GIVEN
        val form = anExpenseForm(accountId = CHECKING).copy(toAccountId = SAVINGS)

        // WHEN
        val switched = form.withType(TransactionFormType.TRANSFER)

        // THEN
        assertThat(switched.toAccountId).isEqualTo(SAVINGS)
    }
}

class TransactionFormTransferChoicesTest
{
    private val checking = anAccount(CHECKING)
    private val savings = anAccount(SAVINGS)
    private val cash = anAccount(AccountId(Uuid.random()))
    private val all = listOf(checking, savings, cash)

    @Test
    fun `destinations exclude the chosen source account`()
    {
        // GIVEN
        val form = aTransferForm(from = CHECKING, to = null)

        // WHEN
        val choices = form.destinationChoices(all)

        // THEN
        assertThat(choices).containsExactly(savings, cash)
    }

    @Test
    fun `sources exclude the chosen destination account`()
    {
        // GIVEN
        val form = aTransferForm(from = null, to = SAVINGS)

        // WHEN
        val choices = form.sourceChoices(all)

        // THEN
        assertThat(choices).containsExactly(checking, cash)
    }

    @Test
    fun `nothing is excluded while the other side is still empty`()
    {
        // GIVEN
        val form = aTransferForm(from = null, to = null)

        // THEN
        assertThat(form.sourceChoices(all)).containsExactlyElementsOf(all)
        assertThat(form.destinationChoices(all)).containsExactlyElementsOf(all)
    }

    @Test
    fun `sources are not filtered outside of a transfer`()
    {
        // GIVEN a destination left over from an earlier transfer attempt
        val form = anExpenseForm(accountId = null).copy(toAccountId = SAVINGS)

        // THEN
        assertThat(form.sourceChoices(all)).containsExactlyElementsOf(all)
    }
}

class TransactionFormInitialTest
{
    private val active = anAccount(CHECKING)
    private val other = anAccount(SAVINGS)
    private val archived = anAccount(AccountId(Uuid.random()), archived = true)

    @Test
    fun `archived accounts are not selectable`()
    {
        // WHEN
        val selectable = selectableAccounts(listOf(active, archived, other))

        // THEN
        assertThat(selectable).containsExactly(active, other)
    }

    @Test
    fun `starts on the account the user came from`()
    {
        // WHEN
        val form = TransactionFormState.initial(
            listOf(active, other),
            preselectedAccountId = SAVINGS,
            now = NOW
        )

        // THEN
        assertThat(form.accountId).isEqualTo(SAVINGS)
    }

    @Test
    fun `starts on no account when none was preselected`()
    {
        // WHEN
        val form = TransactionFormState.initial(
            listOf(active, other),
            preselectedAccountId = null,
            now = NOW
        )

        // THEN
        assertThat(form.accountId).isNull()
    }

    @Test
    fun `ignores a preselected account that is archived`()
    {
        // WHEN
        val form = TransactionFormState.initial(
            listOf(active, archived),
            preselectedAccountId = archived.id,
            now = NOW,
        )

        // THEN
        assertThat(form.accountId).isNull()
    }

    @Test
    fun `starts as an empty expense dated now`()
    {
        // WHEN
        val form =
            TransactionFormState.initial(listOf(active), preselectedAccountId = null, now = NOW)

        // THEN
        assertThat(form.type).isEqualTo(TransactionFormType.EXPENSE)
        assertThat(form.amountText).isEmpty()
        assertThat(form.title).isEmpty()
        assertThat(form.description).isEmpty()
        assertThat(form.subcategory).isNull()
        assertThat(form.toAccountId).isNull()
        assertThat(form.date).isEqualTo(NOW)
    }
}
