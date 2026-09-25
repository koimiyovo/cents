package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which account of the form is meant: the one money leaves from (or the only one), or the one a transfer goes to. */
enum class AccountField { SOURCE, DESTINATION }

/** Archived accounts can't receive new transactions (domain rule), so the form never offers them. */
fun selectableAccounts(accounts: List<Account>): List<Account>
{
    return accounts.filter { it.archivedAt == null }
}

/**
 * The accounts a form offers: the active ones, plus — when editing — the account the transaction
 * currently sits on even if it is archived, so that it stays visible (and selectable again after
 * trying another). An archived account is otherwise never offered: it takes no new transaction.
 */
fun accountChoicesFor(accounts: List<Account>, originalAccountId: AccountId?): List<Account>
{
    return accounts.filter { it.archivedAt == null || it.id == originalAccountId }
}

/**
 * Only an income or an expense can be edited here. An opening deposit is refused by the domain; a
 * transfer is two legs that nothing links together, and the update command only knows income and
 * expense — editing one leg would turn it into a plain movement and leave the other orphaned.
 */
fun canEditTransaction(transaction: Transaction): Boolean
{
    return transaction.category == TransactionCategory.EXPENSE || transaction.category == TransactionCategory.INCOME
}

/**
 * What the user has typed so far, as raw text: validation and parsing happen in [submit], not while
 * typing, so a half-typed "12," is never treated as an error. Stricter than the domain in places
 * (see [parseAmountToCents]); whatever it lets through is still re-checked by the domain types.
 */
data class TransactionFormState(
    val type: TransactionFormType,
    val accountId: AccountId?,
    val toAccountId: AccountId? = null,
    val amountText: String = "",
    val title: String = "",
    val subcategory: Subcategory? = null,
    val description: String = "",
    val date: Instant,
    /** Set when an existing transaction is being edited instead of a new one recorded. */
    val editingId: TransactionId? = null,
    /** The account the edited transaction sat on when the form was opened. */
    val originalAccountId: AccountId? = null,
)
{
    val isEditing: Boolean get() = editingId != null

    /** Whether there is already something in what sits behind "Plus de détails" (subcategory, description). */
    val hasDetails: Boolean get() = subcategory != null || description.isNotBlank()

    companion object
    {
        /**
         * A form pre-filled with [transaction]'s values, saving as an update. The account is
         * pre-selected but can be changed: the transaction is then moved to the other one.
         * [subcategory] is the one the transaction points to (null when it has none). It is a required
         * argument, not a default: forgetting it would silently drop the subcategory on saving.
         */
        fun editing(transaction: Transaction, subcategory: Subcategory?): TransactionFormState
        {
            require(canEditTransaction(transaction)) { "Only an income or an expense can be edited" }
            return TransactionFormState(
                type = if (transaction.category == TransactionCategory.INCOME) TransactionFormType.INCOME
                else TransactionFormType.EXPENSE,
                accountId = transaction.accountId,
                amountText = formatCentsForInput(transaction.amount.value),
                title = transaction.title.value,
                subcategory = subcategory,
                description = transaction.description?.value.orEmpty(),
                date = transaction.date,
                editingId = transaction.id,
                originalAccountId = transaction.accountId,
            )
        }

        fun initial(
            accounts: List<Account>,
            preselectedAccountId: AccountId?,
            now: Instant
        ): TransactionFormState
        {
            val preselected = preselectedAccountId?.takeIf { id ->
                selectableAccounts(accounts).any { it.id == id }
            }
            return TransactionFormState(
                type = TransactionFormType.EXPENSE,
                accountId = preselected,
                date = now,
            )
        }
    }

    fun submit(): FormSubmission
    {
        val errors = mutableSetOf<FormError>()

        val amountCents = parseAmountToCents(amountText)
        if (amountCents == null) errors += FormError.AMOUNT_INVALID
        if (title.isBlank()) errors += FormError.TITLE_REQUIRED
        if (accountId == null) errors += FormError.ACCOUNT_REQUIRED

        val category = type.recordableCategory()
        if (category == null)
        {
            if (toAccountId == null) errors += FormError.DESTINATION_ACCOUNT_REQUIRED
            else if (toAccountId == accountId) errors += FormError.SAME_ACCOUNT
        } else if (subcategory != null && subcategory.kind != category)
        {
            errors += FormError.SUBCATEGORY_MISMATCH
        }

        if (editingId != null)
        {
            if (errors.isNotEmpty() || amountCents == null || category == null || accountId == null)
            {
                return FormSubmission.Invalid(errors)
            }
            return FormSubmission.Update(
                UpdateTransactionCommand(
                    id = editingId,
                    accountId = accountId,
                    amount = Money(amountCents),
                    title = TransactionTitle(title),
                    category = category,
                    subcategoryId = subcategory?.id,
                    description = TransactionDescription.of(description),
                    date = date,
                ),
            )
        }

        // The extra null checks are only for smart casts: each one implies an error reported above.
        if (errors.isNotEmpty() || amountCents == null || accountId == null)
        {
            return FormSubmission.Invalid(errors)
        }

        val amount = Money(amountCents)
        val cleanTitle = TransactionTitle(title)
        if (category == null)
        {
            return FormSubmission.Transfer(
                RecordTransferCommand(
                    fromAccountId = accountId,
                    toAccountId = requireNotNull(toAccountId),
                    amount = amount,
                    title = cleanTitle,
                    date = date,
                ),
            )
        }
        return FormSubmission.Record(
            RecordTransactionCommand(
                accountId = accountId,
                amount = amount,
                title = cleanTitle,
                category = category,
                subcategoryId = subcategory?.id,
                description = TransactionDescription.of(description),
                date = date,
            ),
        )
    }

    /** The calendar day [date] falls on. */
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = date.atZone(zone).toLocalDate()

    /** Moves the transaction to [day], keeping the time of day of [now] (see [dateOnDay]). */
    fun withDay(
        day: LocalDate,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault()
    ): TransactionFormState
    {
        if (editingId != null)
        {
            // An edited transaction keeps its own time of day, so it stays where it was among that
            // day's others — but never lands in the future, which the date picker doesn't allow either.
            val moved = dateOnDay(day, date, zone)
            return copy(date = if (moved.isAfter(now)) now else moved)
        }
        return copy(date = dateOnDay(day, now, zone))
    }

    /**
     * The form as it should be saved at [now]: when it is still dated today, the date is refreshed
     * to the moment of saving (the sheet may have been open for a while); a day picked on purpose is
     * left alone.
     */
    fun stampedAt(now: Instant, zone: ZoneId = ZoneId.systemDefault()): TransactionFormState
    {
        // An edited transaction has a date of its own: saving must not silently move it to "now".
        if (isEditing) return this
        return if (day(zone) == now.atZone(zone).toLocalDate()) copy(date = now) else this
    }

    /**
     * Changes the type, keeping everything typed except what the new type can't take: a
     * subcategory of the wrong kind, and a transfer destination equal to the chosen account (the
     * form never lets one pick the same account twice, but a destination typed earlier may have
     * become the account since).
     */
    fun withType(type: TransactionFormType): TransactionFormState
    {
        // An income can become an expense and back, but not a transfer (see [canEditTransaction]).
        if (isEditing && type == TransactionFormType.TRANSFER) return this
        val category = type.recordableCategory()
        val keptSubcategory = subcategory?.takeIf { category != null && it.kind == category }
        val keptDestination =
            toAccountId?.takeUnless { type == TransactionFormType.TRANSFER && it == accountId }
        return copy(type = type, subcategory = keptSubcategory, toAccountId = keptDestination)
    }

    /**
     * The account the edited transaction sits on, if that account is archived. Editing what an
     * archived account holds is allowed (its history stays correctable), but it changes the balance
     * of an account the user considers closed, so the form says so.
     */
    fun archivedOriginalAccount(accounts: List<Account>): Account?
    {
        val original = originalAccountId ?: return null
        return accounts.firstOrNull { it.id == original && it.archivedAt != null }
    }

    /** The kind of subcategory this form takes — null for a transfer, which has none. */
    val subcategoryKind: RecordableTransactionCategory? get() = type.recordableCategory()

    /** The subcategories this form offers: those of the kind it records (a transfer has none). */
    fun subcategoryChoices(subcategories: List<Subcategory>): List<Subcategory>
    {
        val category = type.recordableCategory() ?: return emptyList()
        return subcategories.filter { it.kind == category }
    }

    /**
     * The form with [id] chosen as its [field]. Choosing a new source that is also the destination
     * clears the destination (a transfer to the same account is meaningless).
     */
    fun withAccountSelected(field: AccountField, id: AccountId): TransactionFormState = when (field)
    {
        AccountField.SOURCE      -> copy(accountId = id, toAccountId = toAccountId?.takeUnless { it == id })
        AccountField.DESTINATION -> copy(toAccountId = id)
    }

    /**
     * When no account is chosen and exactly one can be, chooses it: there is nothing to decide. What
     * makes it happen after the form is open — an account created from the form itself.
     */
    fun withSoleAccountSelected(accounts: List<Account>): TransactionFormState
    {
        if (accountId != null) return this
        val sole = selectableAccounts(accounts).singleOrNull() ?: return this
        return copy(accountId = sole.id)
    }

    /** The accounts this form offers (see [accountChoicesFor]). */
    fun accountChoices(accounts: List<Account>): List<Account> =
        accountChoicesFor(accounts, originalAccountId)

    /**
     * Accounts a transfer can leave from: every selectable one except the chosen destination — a
     * transfer to the same account is meaningless, so it isn't offered rather than rejected later.
     * Any other type has no destination and offers them all.
     */
    fun sourceChoices(selectable: List<Account>): List<Account>
    {
        if (type != TransactionFormType.TRANSFER) return selectable
        return selectable.filter { it.id != toAccountId }
    }

    /** Accounts a transfer can go to: every selectable one except the chosen source. */
    fun destinationChoices(selectable: List<Account>): List<Account>
    {
        return selectable.filter { it.id != accountId }
    }
}

/** The domain category a form of this type records, or null for a transfer (recorded as two legs). */
private fun TransactionFormType.recordableCategory(): RecordableTransactionCategory? = when (this)
{
    TransactionFormType.EXPENSE  -> RecordableTransactionCategory.EXPENSE
    TransactionFormType.INCOME   -> RecordableTransactionCategory.INCOME
    TransactionFormType.TRANSFER -> null
}

/**
 * Today keeps the exact instant; any other day gets the current time of day, so an entry recorded
 * "for yesterday" sorts naturally among that day's others instead of all landing at midnight.
 */
internal fun dateOnDay(day: LocalDate, now: Instant, zone: ZoneId): Instant
{
    val nowLocal = now.atZone(zone)
    if (day == nowLocal.toLocalDate()) return now
    return day.atTime(nowLocal.toLocalTime()).atZone(zone).toInstant()
}
