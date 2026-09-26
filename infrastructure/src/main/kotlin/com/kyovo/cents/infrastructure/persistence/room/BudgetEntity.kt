package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.ForeignKey
import java.util.UUID

/**
 * A row of the `budgets` table: the limit, in cents, set on one subcategory for one month.
 *
 * A budget is identified by its subcategory and its month together, so those two columns are the
 * primary key: saving again for the same month replaces the limit, and each month keeps its own row.
 * The month is a single number, `year * 100 + month` (September 2026 is `202609`): plain to read in a
 * database inspector, and it sorts in calendar order.
 *
 * The link to the subcategory is enforced by the database itself, a second line of defence behind the
 * services: a budget cannot point to a subcategory that does not exist, and deleting a subcategory takes
 * its budgets with it (`CASCADE`) — the rule of the domain, since a budget means nothing without the
 * subcategory it caps. The primary key starts with `subcategoryId`, so it also serves that lookup.
 *
 * The column is `limitCents`, not `limit`: `LIMIT` is an SQL keyword.
 */
@Entity(
    tableName = "budgets",
    primaryKeys = ["subcategoryId", "month"],
    foreignKeys = [
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    val subcategoryId: UUID,
    val month: Int,
    val limitCents: Long
)
