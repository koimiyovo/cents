package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.ForeignKey
import java.util.UUID

/**
 * A row of the `budget_alerts` table: one crossing (close to the limit, or over it) already reported for
 * one subcategory in one month — the WorkManager check's own memory, so it never notifies the same
 * crossing twice.
 *
 * An alert is identified by its subcategory, its month and its level together, so those three columns are
 * the primary key: recording the same crossing again (which the service never does on purpose, but the
 * database still guards it) replaces the row rather than duplicating it. The month is encoded the same way
 * as [BudgetEntity]'s (`year * 100 + month`), and `level` is the enum constant's name as text, like
 * [TransactionEntity]'s `category` — renaming a domain constant must not silently change what a stored row
 * means.
 *
 * The link to the subcategory is enforced by the database itself, a second line of defence behind
 * [com.kyovo.cents.application.usecase.DeleteSubcategoryService]: deleting a subcategory takes its alerts
 * with it (`CASCADE`), the same reasoning as [BudgetEntity] — an alert means nothing without the subcategory
 * it is about.
 */
@Entity(
    tableName = "budget_alerts",
    primaryKeys = ["subcategoryId", "month", "level"],
    foreignKeys = [
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetAlertEntity(
    val subcategoryId: UUID,
    val month: Int,
    val level: String,
)
