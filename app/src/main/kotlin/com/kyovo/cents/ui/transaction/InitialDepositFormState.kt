package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.parseAmountToCents

/**
 * Only an opening deposit opens this form (an income or an expense opens the full one, see
 * [canEditTransaction]; a transfer leg opens nothing). Its amount is the one thing that can be
 * corrected on it — the domain refuses to change or delete anything else about it.
 */
fun canEditInitialDeposit(transaction: Transaction): Boolean
{
    return transaction.category == TransactionCategory.INITIAL_DEPOSIT
}

/**
 * The amount typed so far, as raw text (parsed in [submit], like the transaction form, so a
 * half-typed "12," is never an error while typing).
 */
data class InitialDepositFormState(
    val amountText: String,
    val editingId: TransactionId,
)
{
    companion object
    {
        fun editing(transaction: Transaction): InitialDepositFormState
        {
            require(canEditInitialDeposit(transaction)) { "Only an opening deposit can be edited here" }
            return InitialDepositFormState(
                amountText = formatCentsForInput(transaction.amount.value),
                editingId = transaction.id,
            )
        }
    }

    /** Zero is refused (see [parseAmountToCents]): opening an account with 0 creates no deposit at all. */
    fun submit(): InitialDepositSubmission
    {
        val cents = parseAmountToCents(amountText) ?: return InitialDepositSubmission.Invalid
        return InitialDepositSubmission.Update(editingId, Money(cents))
    }
}

sealed interface InitialDepositSubmission
{
    data class Update(val id: TransactionId, val amount: Money) : InitialDepositSubmission

    /** The amount is the only field, so it is the only thing that can be wrong. */
    data object Invalid : InitialDepositSubmission
}
