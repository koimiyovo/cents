package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionSubcategory
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.ui.common.parseAmountToCents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Archived accounts can't receive new transactions (domain rule), so the form never offers them. */
fun selectableAccounts(accounts: List<Account>): List<Account>
{
    return accounts.filter { it.archivedAt == null }
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
    val subcategory: TransactionSubcategory? = null,
    val description: String = "",
    val date: Instant,
)
{
    companion object
    {
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
        } else if (subcategory != null && !category.accepts(subcategory))
        {
            errors += FormError.SUBCATEGORY_MISMATCH
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
                subcategory = subcategory,
                description = TransactionDescription.of(description),
                date = date,
            ),
        )
    }

    /** The calendar day [date] falls on. */
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = date.atZone(zone).toLocalDate()

    /** Moves the transaction to [day], keeping the time of day of [now] (see [dateOnDay]). */
    fun withDay(day: LocalDate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): TransactionFormState
    {
        return copy(date = dateOnDay(day, now, zone))
    }

    /**
     * The form as it should be saved at [now]: when it is still dated today, the date is refreshed
     * to the moment of saving (the sheet may have been open for a while); a day picked on purpose is
     * left alone.
     */
    fun stampedAt(now: Instant, zone: ZoneId = ZoneId.systemDefault()): TransactionFormState
    {
        return if (day(zone) == now.atZone(zone).toLocalDate()) copy(date = now) else this
    }

    /** Changes the type, keeping everything typed except a subcategory the new type can't take. */
    fun withType(type: TransactionFormType): TransactionFormState
    {
        val category = type.recordableCategory()
        val keptSubcategory = subcategory?.takeIf { category != null && category.accepts(it) }
        return copy(type = type, subcategory = keptSubcategory)
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
