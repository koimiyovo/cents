package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

private val ACCOUNT = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
private val OTHER_ACCOUNT = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
private val TRANSACTION_ID = TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
private val NOW_INSTANT = Instant.parse("2026-09-23T12:00:00Z")
private val EARLIER = Instant.parse("2026-09-20T08:30:00Z")

private fun anExpense(
    date: Instant = EARLIER,
    subcategory: Subcategory? = GROCERIES_SUBCATEGORY,
    description: String? = "Marché du samedi",
) = Transaction.recorded(
    id = TRANSACTION_ID,
    accountId = ACCOUNT,
    amount = Money(1_250),
    title = TransactionTitle("Courses"),
    category = RecordableTransactionCategory.EXPENSE,
    subcategory = subcategory,
    description = TransactionDescription.of(description),
    date = date,
)

private fun anIncome() = Transaction.recorded(
    id = TRANSACTION_ID,
    accountId = ACCOUNT,
    amount = Money(245_000),
    title = TransactionTitle("Salaire"),
    category = RecordableTransactionCategory.INCOME,
    subcategory = SALARY_SUBCATEGORY,
    description = null,
    date = EARLIER,
)

private fun anAccount(id: AccountId, archived: Boolean = false) = Account(
    id = id,
    name = AccountName("Account ${id.value}"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = EARLIER,
    archivedAt = if (archived) EARLIER else null,
)

private fun updateOf(form: TransactionFormState): UpdateTransactionCommand =
    (form.submit() as FormSubmission.Update).command

class CanEditTransactionTest
{
    @Test
    fun `an expense and an income can be edited`()
    {
        assertThat(canEditTransaction(anExpense())).isTrue()
        assertThat(canEditTransaction(anIncome())).isTrue()
    }

    @Test
    fun `an opening deposit cannot`()
    {
        assertThat(canEditTransaction(Transaction.openingDeposit(TRANSACTION_ID, ACCOUNT, Money(100), EARLIER))).isFalse()
    }

    @Test
    fun `neither leg of a transfer can`()
    {
        val title = TransactionTitle("Retrait espèces")
        assertThat(canEditTransaction(Transaction.transferOut(TRANSACTION_ID, ACCOUNT, Money(100), title, EARLIER))).isFalse()
        assertThat(canEditTransaction(Transaction.transferIn(TRANSACTION_ID, OTHER_ACCOUNT, Money(100), title, EARLIER))).isFalse()
    }
}

class TransactionFormEditTest
{
    @Test
    fun `editing an expense pre-fills every field from it`()
    {
        // WHEN
        val form = TransactionFormState.editing(anExpense())

        // THEN
        assertThat(form.type).isEqualTo(TransactionFormType.EXPENSE)
        assertThat(form.accountId).isEqualTo(ACCOUNT)
        assertThat(form.amountText).isEqualTo("12,50")
        assertThat(form.title).isEqualTo("Courses")
        assertThat(form.subcategory).isEqualTo(GROCERIES_SUBCATEGORY)
        assertThat(form.description).isEqualTo("Marché du samedi")
        assertThat(form.date).isEqualTo(EARLIER)
        assertThat(form.editingId).isEqualTo(TRANSACTION_ID)
        assertThat(form.isEditing).isTrue()
    }

    @Test
    fun `an income is edited as an income`()
    {
        assertThat(TransactionFormState.editing(anIncome()).type).isEqualTo(TransactionFormType.INCOME)
    }

    @Test
    fun `a transaction without subcategory or description gives empty fields`()
    {
        // WHEN
        val form = TransactionFormState.editing(anExpense(subcategory = null, description = null))

        // THEN
        assertThat(form.subcategory).isNull()
        assertThat(form.description).isEmpty()
    }

    @Test
    fun `only an income or an expense can be opened for editing`()
    {
        val transfer = Transaction.transferOut(TRANSACTION_ID, ACCOUNT, Money(100), TransactionTitle("Virement"), EARLIER)
        val deposit = Transaction.openingDeposit(TRANSACTION_ID, ACCOUNT, Money(100), EARLIER)

        assertThatThrownBy { TransactionFormState.editing(transfer) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TransactionFormState.editing(deposit) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a new-transaction form is not an edit`()
    {
        val form = TransactionFormState.initial(emptyList(), null, NOW_INSTANT)
        assertThat(form.isEditing).isFalse()
    }

    @Test
    fun `an untouched edit submits an update command carrying the transaction's id and values`()
    {
        // WHEN
        val submission = TransactionFormState.editing(anExpense()).submit()

        // THEN
        assertThat(submission).isEqualTo(
            FormSubmission.Update(
                UpdateTransactionCommand(
                    id = TRANSACTION_ID,
                    accountId = ACCOUNT,
                    amount = Money(1_250),
                    title = TransactionTitle("Courses"),
                    category = RecordableTransactionCategory.EXPENSE,
                    subcategoryId = GROCERIES_SUBCATEGORY.id,
                    description = TransactionDescription.of("Marché du samedi"),
                    date = EARLIER,
                ),
            ),
        )
    }

    @Test
    fun `changed fields end up in the command`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense())
            .copy(amountText = "20", title = "  Courses du mois ")
            .withType(TransactionFormType.INCOME)

        // WHEN
        val command = updateOf(form)

        // THEN
        assertThat(command.amount).isEqualTo(Money(2_000))
        assertThat(command.title).isEqualTo(TransactionTitle("Courses du mois"))
        assertThat(command.category).isEqualTo(RecordableTransactionCategory.INCOME)
        // an expense subcategory doesn't survive becoming an income
        assertThat(command.subcategoryId).isNull()
    }

    @Test
    fun `emptying the description removes it`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(description = "   ")

        // WHEN / THEN
        assertThat(updateOf(form).description).isNull()
    }

    @Test
    fun `an edit still needs an account`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(accountId = null)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.ACCOUNT_REQUIRED)
    }

    @Test
    fun `the command carries the account chosen in the form, so the transaction can move`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(accountId = OTHER_ACCOUNT)

        // WHEN
        val command = updateOf(form)

        // THEN
        assertThat(command.accountId).isEqualTo(OTHER_ACCOUNT)
    }

    @Test
    fun `an untouched edit stays on the transaction's account`()
    {
        assertThat(updateOf(TransactionFormState.editing(anExpense())).accountId).isEqualTo(ACCOUNT)
    }

    @Test
    fun `editing remembers the account the transaction started on`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(accountId = OTHER_ACCOUNT)

        // THEN choosing another account doesn't forget where it came from
        assertThat(form.originalAccountId).isEqualTo(ACCOUNT)
    }

    @Test
    fun `a blank title is rejected`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(title = "  ")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.TITLE_REQUIRED)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0", "0,00", "abc", "12,345"])
    fun `an invalid amount is rejected`(text: String)
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense()).copy(amountText = text)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.AMOUNT_INVALID)
    }

    @Test
    fun `a subcategory that does not fit the type is rejected`()
    {
        // GIVEN an expense given an income subcategory
        val form = TransactionFormState.editing(anExpense()).copy(subcategory = SALARY_SUBCATEGORY)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat((submission as FormSubmission.Invalid).errors).containsExactly(FormError.SUBCATEGORY_MISMATCH)
    }

    @Test
    fun `saving does not move the transaction to now, even when it is dated today`()
    {
        // GIVEN a transaction of this morning, saved this afternoon
        val morning = Instant.parse("2026-09-23T08:00:00Z")
        val form = TransactionFormState.editing(anExpense(date = morning))

        // WHEN
        val saved = form.stampedAt(NOW_INSTANT, ZoneOffset.UTC)

        // THEN a new transaction would be stamped with the moment of saving; an edited one keeps its own
        assertThat(saved.date).isEqualTo(morning)
    }

    @Test
    fun `moving to another day keeps the time of day the transaction had`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense(date = EARLIER))

        // WHEN
        val moved = form.withDay(LocalDate.parse("2026-09-18"), NOW_INSTANT, ZoneOffset.UTC)

        // THEN
        assertThat(moved.date).isEqualTo(Instant.parse("2026-09-18T08:30:00Z"))
    }

    @Test
    fun `picking the day it already has changes nothing`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense(date = EARLIER))

        // WHEN
        val same = form.withDay(LocalDate.parse("2026-09-20"), NOW_INSTANT, ZoneOffset.UTC)

        // THEN
        assertThat(same.date).isEqualTo(EARLIER)
    }

    @Test
    fun `moving to today never puts the transaction in the future`()
    {
        // GIVEN a transaction from late in the evening, brought to today at noon
        val lateEvening = Instant.parse("2026-09-20T23:00:00Z")
        val form = TransactionFormState.editing(anExpense(date = lateEvening))

        // WHEN
        val moved = form.withDay(LocalDate.parse("2026-09-23"), NOW_INSTANT, ZoneOffset.UTC)

        // THEN 23:00 today hasn't happened yet: it lands at the current time instead
        assertThat(moved.date).isEqualTo(NOW_INSTANT)
    }

    @Test
    fun `an edit can switch between expense and income but not become a transfer`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense())

        // WHEN / THEN
        assertThat(form.withType(TransactionFormType.INCOME).type).isEqualTo(TransactionFormType.INCOME)
        assertThat(form.withType(TransactionFormType.TRANSFER).type).isEqualTo(TransactionFormType.EXPENSE)
    }
}

class AccountChoicesTest
{
    private val active = anAccount(ACCOUNT)
    private val otherActive = anAccount(OTHER_ACCOUNT)
    private val archivedOne = anAccount(AccountId(UUID.fromString("44444444-4444-4444-4444-444444444444")), archived = true)
    private val archivedOther = anAccount(AccountId(UUID.fromString("55555555-5555-5555-5555-555555555555")), archived = true)

    @Test
    fun `a new transaction is offered the active accounts only`()
    {
        // GIVEN
        val form = TransactionFormState.initial(emptyList(), null, NOW_INSTANT)

        // WHEN
        val choices = form.accountChoices(listOf(active, archivedOne, otherActive))

        // THEN an archived account takes no new transaction
        assertThat(choices).containsExactly(active, otherActive)
    }

    @Test
    fun `an edit is also offered the account the transaction sits on, even archived`()
    {
        // GIVEN a transaction on an archived account
        val onArchived = Transaction.recorded(
            id = TRANSACTION_ID, accountId = archivedOne.id, amount = Money(100), title = TransactionTitle("Vieux"),
            category = RecordableTransactionCategory.EXPENSE, subcategory = null, description = null, date = EARLIER,
        )
        val form = TransactionFormState.editing(onArchived)

        // WHEN
        val choices = form.accountChoices(listOf(active, archivedOne, archivedOther, otherActive))

        // THEN its own account stays selectable, the other archived ones don't
        assertThat(choices).containsExactly(active, archivedOne, otherActive)
    }

    @Test
    fun `the original account stays offered after choosing another one`()
    {
        // GIVEN
        val onArchived = Transaction.recorded(
            id = TRANSACTION_ID, accountId = archivedOne.id, amount = Money(100), title = TransactionTitle("Vieux"),
            category = RecordableTransactionCategory.EXPENSE, subcategory = null, description = null, date = EARLIER,
        )
        val moved = TransactionFormState.editing(onArchived).copy(accountId = ACCOUNT)

        // WHEN
        val choices = moved.accountChoices(listOf(active, archivedOne))

        // THEN so the user can change their mind
        assertThat(choices).containsExactly(active, archivedOne)
    }

    @Test
    fun `an active account being edited is offered like any other`()
    {
        // GIVEN
        val form = TransactionFormState.editing(anExpense())

        // WHEN
        val choices = form.accountChoices(listOf(active, otherActive, archivedOne))

        // THEN
        assertThat(choices).containsExactly(active, otherActive)
    }
}

class ArchivedOriginalAccountTest
{
    private val active = anAccount(ACCOUNT)
    private val archivedOne = anAccount(AccountId(UUID.fromString("44444444-4444-4444-4444-444444444444")), archived = true)

    private fun expenseOn(accountId: AccountId) = Transaction.recorded(
        id = TRANSACTION_ID, accountId = accountId, amount = Money(100), title = TransactionTitle("Vieux"),
        category = RecordableTransactionCategory.EXPENSE, subcategory = null, description = null, date = EARLIER,
    )

    @Test
    fun `is the account of the edited transaction when that account is archived`()
    {
        // GIVEN
        val form = TransactionFormState.editing(expenseOn(archivedOne.id))

        // THEN
        assertThat(form.archivedOriginalAccount(listOf(active, archivedOne))).isEqualTo(archivedOne)
    }

    @Test
    fun `is nothing when the account of the edited transaction is active`()
    {
        val form = TransactionFormState.editing(expenseOn(active.id))

        assertThat(form.archivedOriginalAccount(listOf(active, archivedOne))).isNull()
    }

    @Test
    fun `is nothing for a new transaction`()
    {
        val form = TransactionFormState.initial(listOf(active), null, NOW_INSTANT)

        assertThat(form.archivedOriginalAccount(listOf(active, archivedOne))).isNull()
    }

    @Test
    fun `still points at the original account after the user picks another one`()
    {
        // GIVEN the transaction is being moved out of the archived account
        val form = TransactionFormState.editing(expenseOn(archivedOne.id)).copy(accountId = active.id)

        // THEN the archived account still loses a transaction, so the notice stays
        assertThat(form.archivedOriginalAccount(listOf(active, archivedOne))).isEqualTo(archivedOne)
    }

    @Test
    fun `is nothing when the account is not among those given`()
    {
        val form = TransactionFormState.editing(expenseOn(archivedOne.id))

        assertThat(form.archivedOriginalAccount(listOf(active))).isNull()
    }
}
