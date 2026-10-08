package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidBackupException

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
{
    /** What the snapshot holds, counted by kind. */
    fun summary(): BackupSummary
    {
        return BackupSummary(
            accounts = accounts.size,
            subcategories = subcategories.size,
            transactions = transactions.size,
            budgets = budgets.size,
            recurringTransactions = recurringTransactions.size,
            projects = projects.size
        )
    }

    /**
     * Whether the pieces agree with each other, which reading each one cannot tell: no id twice, no name
     * taken twice (the rule that applies when one is created: active accounts, subcategories of a kind,
     * projects), no account with more than one opening deposit, and every reference leads to something in the snapshot, of the right kind. The same
     * guarantees the app keeps one write at a time and the database keeps with its keys.
     *
     * @throws InvalidBackupException on the first thing that does not agree.
     */
    fun requireConsistent()
    {
        requireUnique(accounts.map { it.id })
        requireUnique(subcategories.map { it.id })
        requireUnique(transactions.map { it.id })
        requireUnique(recurringTransactions.map { it.id })
        requireUnique(projects.map { it.id })
        requireUnique(budgets.map { it.subcategoryId to it.month })
        // Account opening creates exactly one deposit, and nothing else can create another.
        requireUnique(transactions.filter { it.category == TransactionCategory.INITIAL_DEPOSIT }.map { it.accountId })

        requireNoNameTwice(accounts.filter { it.archivedAt == null }.map { it.name }, AccountName::matches)
        RecordableTransactionCategory.entries.forEach { kind ->
            requireNoNameTwice(subcategories.filter { it.kind == kind }.map { it.name }, SubcategoryName::matches)
        }
        requireNoNameTwice(projects.map { it.name }, ProjectName::matches)

        val accountIds = accounts.map { it.id }.toSet()
        val subcategoriesById = subcategories.associateBy { it.id }
        val projectIds = projects.map { it.id }.toSet()

        transactions.forEach {
            require(it.accountId in accountIds)
            require(it.projectId == null || it.projectId in projectIds)
            require(it.subcategoryId == null || subcategoriesById[it.subcategoryId]?.kind?.toTransactionCategory() == it.category)
        }
        budgets.forEach {
            require(subcategoriesById[it.subcategoryId]?.kind == RecordableTransactionCategory.EXPENSE)
        }
        recurringTransactions.forEach {
            require(it.accountId in accountIds)
            require(it.subcategoryId == null || subcategoriesById[it.subcategoryId]?.kind == it.category)
        }
    }

    private fun requireUnique(keys: List<Any>)
    {
        require(keys.toSet().size == keys.size)
    }

    private fun <T> requireNoNameTwice(names: List<T>, matches: (T, T) -> Boolean)
    {
        names.forEachIndexed { i, name ->
            require(names.drop(i + 1).none { matches(name, it) })
        }
    }

    private fun require(condition: Boolean)
    {
        if (!condition) throw InvalidBackupException()
    }
}
