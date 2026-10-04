package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionCommand
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.LocalDate

/** Reported once a save is attempted; several can be true at once, like the other forms. */
enum class RecurringTransactionFormError
{
    ACCOUNT_REQUIRED,
    AMOUNT_INVALID,
    TITLE_REQUIRED,
    INTERVAL_INVALID,
    END_BEFORE_START,

    /** The account, subcategory or rule vanished between opening the form and saving it. */
    ACCOUNT_GONE,
    SUBCATEGORY_GONE,
    RULE_GONE,
}

sealed interface RecurringTransactionSubmission
{
    data class Create(val command: CreateRecurringTransactionCommand) : RecurringTransactionSubmission
    data class Update(val command: UpdateRecurringTransactionCommand) : RecurringTransactionSubmission
    data class Invalid(val errors: Set<RecurringTransactionFormError>) : RecurringTransactionSubmission
}

internal val INTERVAL_INPUT_PATTERN = Regex("""\d{0,3}""")

/**
 * What the user has typed so far for a recurring rule: whether it is an expense or an income ([category]), an
 * account, an amount, a title, an optional subcategory of that kind, a pace (frequency + interval, "every N
 * weeks/months/years") and a start date, with an optional end date. No description field in this first version.
 *
 * The account, the category and the start date only matter while creating: [UpdateRecurringTransactionCommand] carries
 * neither (see its own doc, changing them would rewrite already-generated history in a confusing
 * way, and an income turned into an expense would contradict what was already generated) — an edit keeps
 * them here only for display (as [accountId] and, resolved once by whoever opens
 * the edit, [accountName]), [submit] never sends them.
 */
data class RecurringTransactionFormState(
    val editingId: RecurringTransactionId? = null,
    val accountId: AccountId?,
    val accountName: String? = null,
    val category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE,
    val amountText: String = "",
    val title: String = "",
    val subcategory: Subcategory? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalText: String = "1",
    val startDate: LocalDate,
    val hasEndDate: Boolean = false,
    val endDate: LocalDate,
)
{
    val isEditing: Boolean get() = editingId != null

    companion object
    {
        fun creating(accountId: AccountId?, today: LocalDate): RecurringTransactionFormState
        {
            return RecurringTransactionFormState(accountId = accountId, startDate = today, endDate = today)
        }

        /**
         * [subcategory] is the one the rule points to (null when it has none), like the transaction
         * form. [accountName] is resolved by the caller (which already has it, e.g. from a
         * [RecurringTransactionRow]) rather than looked up here, since the account may be archived and no
         * longer among the ones this form is shown with a picker for.
         */
        fun editing(
            recurringTransaction: RecurringTransaction,
            subcategory: Subcategory?,
            accountName: String,
        ): RecurringTransactionFormState
        {
            return RecurringTransactionFormState(
                editingId = recurringTransaction.id,
                accountId = recurringTransaction.accountId,
                accountName = accountName,
                category = recurringTransaction.category,
                amountText = formatCentsForInput(recurringTransaction.amount.value),
                title = recurringTransaction.title.value,
                subcategory = subcategory,
                frequency = recurringTransaction.frequency,
                intervalText = recurringTransaction.interval.toString(),
                startDate = recurringTransaction.startDate,
                hasEndDate = recurringTransaction.endDate != null,
                endDate = recurringTransaction.endDate ?: recurringTransaction.startDate,
            )
        }
    }

    fun withAmount(text: String): RecurringTransactionFormState = copy(amountText = text)

    fun withTitle(text: String): RecurringTransactionFormState = copy(title = text)

    fun withSubcategory(subcategory: Subcategory?): RecurringTransactionFormState = copy(subcategory = subcategory)

    fun withAccount(id: AccountId): RecurringTransactionFormState = copy(accountId = id)

    /**
     * Switches between expense and income, keeping everything typed except a subcategory of the other kind.
     * Only while creating: an existing rule keeps its category (see the class doc).
     */
    fun withCategory(category: RecordableTransactionCategory): RecurringTransactionFormState
    {
        if (isEditing) return this
        return copy(category = category, subcategory = subcategory?.takeIf { it.kind == category })
    }

    /** Changing the pace's unit does not reset the count: "every 2" stays "every 2" under the new unit. */
    fun withFrequency(frequency: RecurrenceFrequency): RecurringTransactionFormState = copy(frequency = frequency)

    /** Digits only, and no edit that would leave more than three of them (an interval in the hundreds). */
    fun withInterval(text: String): RecurringTransactionFormState
    {
        if (!INTERVAL_INPUT_PATTERN.matches(text)) return this
        return copy(intervalText = text)
    }

    /** Only meaningful while creating: an existing rule's start date can't change (see the class doc). */
    fun withStartDate(date: LocalDate): RecurringTransactionFormState
    {
        val adjustedEnd = if (hasEndDate && endDate.isBefore(date)) date else endDate
        return copy(startDate = date, endDate = adjustedEnd)
    }

    fun withEndDateEnabled(enabled: Boolean): RecurringTransactionFormState
    {
        val adjustedEnd = if (enabled && endDate.isBefore(startDate)) startDate else endDate
        return copy(hasEndDate = enabled, endDate = adjustedEnd)
    }

    fun withEndDate(date: LocalDate): RecurringTransactionFormState = copy(endDate = date)

    /** Only the subcategories of the rule's own kind: an income is not filed under "Loyer". */
    fun subcategoryChoices(subcategories: List<Subcategory>): List<Subcategory> =
        subcategories.filter { it.kind == category }

    fun submit(): RecurringTransactionSubmission
    {
        val errors = mutableSetOf<RecurringTransactionFormError>()

        val amountCents = parseAmountToCents(amountText)
        if (amountCents == null) errors += RecurringTransactionFormError.AMOUNT_INVALID
        if (title.isBlank()) errors += RecurringTransactionFormError.TITLE_REQUIRED
        if (accountId == null) errors += RecurringTransactionFormError.ACCOUNT_REQUIRED

        val interval = intervalText.toIntOrNull()
        if (interval == null || interval < 1) errors += RecurringTransactionFormError.INTERVAL_INVALID

        if (hasEndDate && endDate.isBefore(startDate)) errors += RecurringTransactionFormError.END_BEFORE_START

        if (errors.isNotEmpty() || amountCents == null || accountId == null || interval == null)
        {
            return RecurringTransactionSubmission.Invalid(errors)
        }

        val amount = Money(amountCents)
        val cleanTitle = TransactionTitle(title)
        val resolvedEndDate = if (hasEndDate) endDate else null

        if (editingId != null)
        {
            return RecurringTransactionSubmission.Update(
                UpdateRecurringTransactionCommand(
                    id = editingId,
                    amount = amount,
                    title = cleanTitle,
                    subcategoryId = subcategory?.id,
                    description = null,
                    frequency = frequency,
                    interval = interval,
                    endDate = resolvedEndDate,
                ),
            )
        }

        return RecurringTransactionSubmission.Create(
            CreateRecurringTransactionCommand(
                accountId = accountId,
                category = category,
                amount = amount,
                title = cleanTitle,
                subcategoryId = subcategory?.id,
                description = null,
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = resolvedEndDate,
            ),
        )
    }
}
