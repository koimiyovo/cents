package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Project

/** A project that a saved transaction has just brought close to its target, or over it. */
data class ProjectAlert(val project: Project, val level: BudgetAlertLevel)

/**
 * The band a save has newly reached, or null. Only going *up* is news - from nothing to close, or to over, or
 * from close to over - never staying where the project was, and never going down. Nothing is remembered between
 * two saves: the level before and the level after say it all, so an alert comes back by itself if a refund
 * brings the project down and a later expense takes it up again.
 */
fun newlyReachedLevel(before: BudgetAlertLevel?, after: BudgetAlertLevel?): BudgetAlertLevel?
{
    if (after == null) return null
    // BudgetAlertLevel is declared from the mildest band to the worst.
    return after.takeIf { before == null || it.ordinal > before.ordinal }
}

/** What an alert says on screen: the project's emoji (its own, or the default) and name, and the level. */
data class ProjectAlertNotice(val emoji: String, val projectName: String, val level: BudgetAlertLevel)

fun projectAlertNotice(alert: ProjectAlert): ProjectAlertNotice =
    ProjectAlertNotice(alert.project.emoji?.value ?: DEFAULT_PROJECT_EMOJI, alert.project.name.value, alert.level)
