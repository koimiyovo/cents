package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.Transaction

/** The icon of a project without an emoji of its own: a folder, since a project gathers transactions. */
const val DEFAULT_PROJECT_EMOJI = "📁"

/** What a project's target leaves: money still to spend, or the amount by which it has been passed. */
sealed interface ProjectRemaining
{
    /** Zero at exactly the target - spending exactly the target is not passing it. */
    data class Left(val cents: Long) : ProjectRemaining
    data class Over(val cents: Long) : ProjectRemaining
}

/**
 * A project and where it stands: what the projects screen shows for it. The cost is the *net* cost - a refund
 * attached to the project lowers it - and is worked out by the use case, never stored.
 */
data class ProjectCard(val project: Project, val progress: ProjectProgress)
{
    val netCents: Long get() = progress.net

    val transactionCount: Int get() = progress.transactionCount

    val isOverTarget: Boolean get() = progress.isOverTarget

    /** The share of the target already spent, held between empty and full; null for a project without a target. */
    val barFraction: Float?
        get()
        {
            val target = progress.target ?: return null
            return (progress.net.toFloat() / target.value).coerceIn(0f, 1f)
        }

    val remaining: ProjectRemaining?
        get()
        {
            val left = progress.remaining ?: return null
            return if (left >= 0) ProjectRemaining.Left(left) else ProjectRemaining.Over(-left)
        }

    /** The project's own emoji, or the default one - never blank. */
    fun displayEmoji(): String = project.emoji?.value ?: DEFAULT_PROJECT_EMOJI
}

/**
 * One card per project, in the order given (the use case lists them by name). A project the progress flow
 * has not caught up with yet (the two are observed separately) shows as empty rather than disappearing.
 */
fun projectCards(projects: List<Project>, progress: Map<ProjectId, ProjectProgress>): List<ProjectCard> =
    projects.map { project ->
        ProjectCard(project, progress[project.id] ?: ProjectProgress(project.target, Money(0), Money(0), 0))
    }

/** The transactions that belong to [projectId] - refunds included - in the order given. */
fun transactionsOfProject(transactions: List<Transaction>, projectId: ProjectId): List<Transaction> =
    transactions.filter { it.projectId == projectId }
