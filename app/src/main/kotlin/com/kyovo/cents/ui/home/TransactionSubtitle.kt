package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.ui.project.DEFAULT_PROJECT_EMOJI

/**
 * The second line of a transaction row: its subcategory, its account and its project, in that order, joined
 * by bullets. The project shows by its emoji and name so it reads at a glance; what the row does not have is
 * left out, and a row with none of them has an empty line (the row then shows none).
 */
internal fun transactionSubtitle(subcategory: Subcategory?, account: Account?, project: Project?): String =
    listOfNotNull(
        subcategory?.name?.value,
        account?.name?.value,
        project?.let { "${it.emoji?.value ?: DEFAULT_PROJECT_EMOJI} ${it.name.value}" },
    ).joinToString(" • ")
