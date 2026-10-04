package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.ui.budget.SpendingBreakdown
import com.kyovo.cents.ui.budget.spendingBreakdown

// What the "Projets" tab of the Budget screen works out for one project, from its transactions. Nothing here is
// stored: every figure is derived, so it cannot disagree with the transactions.

/**
 * The project the tab opens on: the one of the latest transaction (by date, a refund counting like any other),
 * ignoring transactions in no project or in a project that is gone; the first project when no transaction is in
 * one; none when there is no project at all.
 */
fun initialProjectChoice(projects: List<Project>, transactions: List<Transaction>): Project?
{
    val byId = projects.associateBy { it.id }
    val latest = transactions
        .filter { it.projectId != null && it.projectId in byId }
        .maxByOrNull { it.date }
    return latest?.projectId?.let(byId::get) ?: projects.firstOrNull()
}

/** The project the user chose if it still exists, otherwise the one the tab opens on. */
fun resolveSelectedProject(chosen: ProjectId?, projects: List<Project>, transactions: List<Transaction>): Project? =
    projects.find { it.id == chosen } ?: initialProjectChoice(projects, transactions)

/**
 * Where a project's money went: its expenses split by subcategory (the budget pie's own rules - most spent first,
 * the smallest folded into "other"), and the [refunds] apart. Refunds are not netted into the pie: a refund carries
 * an income subcategory, or none, never the expense subcategory it pays back, so there is nothing to take it off.
 */
data class ProjectBreakdown(val spending: SpendingBreakdown, val refunds: Money)

fun projectBreakdown(transactions: List<Transaction>, subcategories: List<Subcategory>): ProjectBreakdown
{
    val spentBySubcategory: Map<SubcategoryId?, Money> = transactions
        .filter { it.category == TransactionCategory.EXPENSE }
        .groupBy { it.subcategoryId }
        .mapValues { (_, expenses) -> Money(expenses.sumOf { it.amount.value }) }
    val refunds = Money(transactions.filter { it.category == TransactionCategory.INCOME }.sumOf { it.amount.value })
    return ProjectBreakdown(spendingBreakdown(subcategories, spentBySubcategory), refunds)
}

/** The biggest expenses of a project, most expensive first (the most recent first among equals). Refunds are not expenses. */
fun topExpenses(transactions: List<Transaction>, limit: Int = 5): List<Transaction> =
    transactions
        .filter { it.category == TransactionCategory.EXPENSE }
        .sortedWith(compareByDescending<Transaction> { it.amount.value }.thenByDescending { it.date })
        .take(limit)
