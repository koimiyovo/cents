package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import java.util.UUID

// Projects shared by the form tests: a trip and a renovation.

internal val JAPAN_PROJECT = Project(
    ProjectId(UUID.fromString("cccccccc-0000-0000-0000-000000000001")),
    ProjectName("Voyage au Japon"),
    null,
    null,
)

internal val KITCHEN_PROJECT = Project(
    ProjectId(UUID.fromString("cccccccc-0000-0000-0000-000000000002")),
    ProjectName("Travaux cuisine"),
    null,
    null,
)

internal val ALL_TEST_PROJECTS = listOf(JAPAN_PROJECT, KITCHEN_PROJECT)

/** The test project an id designates, the way the screen resolves a transaction's project. */
internal fun projectFor(id: ProjectId?): Project? = ALL_TEST_PROJECTS.find { it.id == id }

/**
 * Tests that only care about the subcategory (written before projects existed) leave the project out:
 * the real API asks for it explicitly, a default would let a caller silently drop it.
 */
internal fun TransactionFormState.Companion.editing(
    transaction: Transaction,
    subcategory: Subcategory?,
): TransactionFormState = editing(transaction, subcategory, projectFor(transaction.projectId))

internal fun TransactionFormViewModel.openForEdit(transaction: Transaction, subcategory: Subcategory?) =
    openForEdit(transaction, subcategory, projectFor(transaction.projectId))
