package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Transaction

/** Where a tap on a row of a transaction list leads. */
enum class TransactionTapTarget
{
    /** An income or an expense: the full form. */
    TRANSACTION_FORM,

    /** An opening deposit: only its amount can be corrected, in a form of its own. */
    INITIAL_DEPOSIT_FORM,

    /** A transfer leg: its two legs are not linked, so editing one would unbalance both. */
    NONE,
}

fun transactionTapTarget(transaction: Transaction): TransactionTapTarget = when
{
    canEditTransaction(transaction)    -> TransactionTapTarget.TRANSACTION_FORM
    canEditInitialDeposit(transaction) -> TransactionTapTarget.INITIAL_DEPOSIT_FORM
    else                               -> TransactionTapTarget.NONE
}

/** Whether a row of this transaction reacts to a tap at all. */
fun Transaction.reactsToTap(): Boolean = transactionTapTarget(this) != TransactionTapTarget.NONE
