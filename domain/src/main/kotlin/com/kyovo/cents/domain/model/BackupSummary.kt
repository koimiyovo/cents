package com.kyovo.cents.domain.model

/**
 * What a backup holds, counted by kind: what an import tells the user it has just restored. Only the
 * things the user created are counted; the budget calendar's settings are a setting, not data.
 */
data class BackupSummary(
    val accounts: Int,
    val subcategories: Int,
    val transactions: Int,
    val budgets: Int,
    val recurringTransactions: Int,
    val projects: Int
)
{
    val isEmpty: Boolean
        get() = accounts == 0 && subcategories == 0 && transactions == 0 &&
                budgets == 0 && recurringTransactions == 0 && projects == 0
}
