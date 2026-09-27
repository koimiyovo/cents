package com.kyovo.cents.ui.recurring

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringExpense
import com.kyovo.cents.domain.model.RecurringExpenseId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.CreateRecurringExpenseCommand
import com.kyovo.cents.domain.port.input.UpdateRecurringExpenseCommand
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.LocalDate

/** Reported once a save is attempted; several can be true at once, like the other forms. */
enum class RecurringExpenseFormError
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

sealed interface RecurringExpenseSubmission
{
    data class Create(val command: CreateRecurringExpenseCommand) : RecurringExpenseSubmission
    data class Update(val command: UpdateRecurringExpenseCommand) : RecurringExpenseSubmission
    data class Invalid(val errors: Set<RecurringExpenseFormError>) : RecurringExpenseSubmission
}

private val INTERVAL_INPUT_PATTERN = Regex("""\d{0,3}""")

/**
 * What the user has typed so far for a recurring-expense rule: an account, an amount, a title, an
 * optional expense subcategory, a pace (frequency + interval, "every N weeks/months/years") and a
 * start date, with an optional end date. No description field in this first version.
 *
 * The account and the start date only matter while creating: [UpdateRecurringExpenseCommand] carries
 * neither (see its own doc, changing them would rewrite already-generated history in a confusing
 * way) — an edit keeps them here only for display, [submit] never sends them.
 */
data class RecurringExpenseFormState(
    val editingId: RecurringExpenseId? = null,
    val accountId: AccountId?,
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
        fun creating(accountId: AccountId?, today: LocalDate): RecurringExpenseFormState
        {
            return RecurringExpenseFormState(accountId = accountId, startDate = today, endDate = today)
        }

        /** [subcategory] is the one the rule points to (null when it has none), like the transaction form. */
        fun editing(recurringExpense: RecurringExpense, subcategory: Subcategory?): RecurringExpenseFormState
        {
            return RecurringExpenseFormState(
                editingId = recurringExpense.id,
                accountId = recurringExpense.accountId,
                amountText = formatCentsForInput(recurringExpense.amount.value),
                title = recurringExpense.title.value,
                subcategory = subcategory,
                frequency = recurringExpense.frequency,
                intervalText = recurringExpense.interval.toString(),
                startDate = recurringExpense.startDate,
                hasEndDate = recurringExpense.endDate != null,
                endDate = recurringExpense.endDate ?: recurringExpense.startDate,
            )
        }
    }

    fun withAmount(text: String): RecurringExpenseFormState = copy(amountText = text)

    fun withTitle(text: String): RecurringExpenseFormState = copy(title = text)

    fun withSubcategory(subcategory: Subcategory?): RecurringExpenseFormState = copy(subcategory = subcategory)

    fun withAccount(id: AccountId): RecurringExpenseFormState = copy(accountId = id)

    /** Changing the pace's unit does not reset the count: "every 2" stays "every 2" under the new unit. */
    fun withFrequency(frequency: RecurrenceFrequency): RecurringExpenseFormState = copy(frequency = frequency)

    /** Digits only, and no edit that would leave more than three of them (an interval in the hundreds). */
    fun withInterval(text: String): RecurringExpenseFormState
    {
        if (!INTERVAL_INPUT_PATTERN.matches(text)) return this
        return copy(intervalText = text)
    }

    /** Only meaningful while creating: an existing rule's start date can't change (see the class doc). */
    fun withStartDate(date: LocalDate): RecurringExpenseFormState
    {
        val adjustedEnd = if (hasEndDate && endDate.isBefore(date)) date else endDate
        return copy(startDate = date, endDate = adjustedEnd)
    }

    fun withEndDateEnabled(enabled: Boolean): RecurringExpenseFormState
    {
        val adjustedEnd = if (enabled && endDate.isBefore(startDate)) startDate else endDate
        return copy(hasEndDate = enabled, endDate = adjustedEnd)
    }

    fun withEndDate(date: LocalDate): RecurringExpenseFormState = copy(endDate = date)

    /** A recurring expense is always an expense (see [RecurringExpense]'s own doc): only those subcategories. */
    fun subcategoryChoices(subcategories: List<Subcategory>): List<Subcategory> =
        subcategories.filter { it.kind == RecordableTransactionCategory.EXPENSE }

    fun submit(): RecurringExpenseSubmission
    {
        val errors = mutableSetOf<RecurringExpenseFormError>()

        val amountCents = parseAmountToCents(amountText)
        if (amountCents == null) errors += RecurringExpenseFormError.AMOUNT_INVALID
        if (title.isBlank()) errors += RecurringExpenseFormError.TITLE_REQUIRED
        if (accountId == null) errors += RecurringExpenseFormError.ACCOUNT_REQUIRED

        val interval = intervalText.toIntOrNull()
        if (interval == null || interval < 1) errors += RecurringExpenseFormError.INTERVAL_INVALID

        if (hasEndDate && endDate.isBefore(startDate)) errors += RecurringExpenseFormError.END_BEFORE_START

        if (errors.isNotEmpty() || amountCents == null || accountId == null || interval == null)
        {
            return RecurringExpenseSubmission.Invalid(errors)
        }

        val amount = Money(amountCents)
        val cleanTitle = TransactionTitle(title)
        val resolvedEndDate = if (hasEndDate) endDate else null

        if (editingId != null)
        {
            return RecurringExpenseSubmission.Update(
                UpdateRecurringExpenseCommand(
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

        return RecurringExpenseSubmission.Create(
            CreateRecurringExpenseCommand(
                accountId = accountId,
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
