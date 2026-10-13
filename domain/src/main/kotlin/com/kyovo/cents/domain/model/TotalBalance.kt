package com.kyovo.cents.domain.model

/**
 * The balance of all the active accounts, in the main currency. [unconvertedAccounts] is how many accounts
 * could not be converted (no rate for their currency yet) and are therefore not in [amount]: the figure is
 * incomplete when it is not 0, and the screen can say so.
 */
data class TotalBalance(
    val amount: AccountBalance,
    val unconvertedAccounts: Int
)
