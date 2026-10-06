package com.kyovo.cents.domain.model

/**
 * Everything the user owns, as one value: what an export writes and an import restores. The lists keep
 * their stored order (the accounts', in particular, is the user's own). The budget alerts already
 * notified are not part of it: they are derived, and the next check recomputes them.
 */
data class BackupSnapshot(
    val accounts: List<Account>,
    val subcategories: List<Subcategory>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val budgetCalendar: BudgetCalendar,
    val recurringTransactions: List<RecurringTransaction>,
    val projects: List<Project>
)
