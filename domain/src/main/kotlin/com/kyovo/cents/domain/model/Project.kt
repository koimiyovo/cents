package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidProjectTargetException

/**
 * Groups transactions across subcategories (a trip, a renovation). The [emoji] reuses the subcategory's:
 * an opaque, optional piece of text. The [target] is the amount the project is meant to cost overall, and
 * [alertThreshold] the share of it from which the project counts as close to it (each project has its own,
 * as each budget does; it only means something with a target).
 */
data class Project(
    val id: ProjectId,
    val name: ProjectName,
    val emoji: Emoji?,
    val target: Money?,
    val alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
)
{
    init
    {
        if (target?.isZero() == true) throw InvalidProjectTargetException()
    }
}
