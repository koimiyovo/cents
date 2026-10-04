package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A row of the `recurring_transactions` table: a planned income or expense — a salary, a rent, a
 * subscription — from which real `transactions` rows are generated as their due dates come. (Until version 5
 * the table was called `recurring_expenses`, back when only expenses could recur.)
 *
 * `category` (`INCOME` or `EXPENSE`) and `frequency` are enum constants' names as text, on purpose (the same
 * reason `category` is, on `transactions`): renaming a domain constant must not silently change what the
 * database means.
 * `startDate`, `endDate` and `lastGeneratedDate` are epoch days (`LocalDate.toEpochDay()`), the same kind
 * of plain, sortable number as the nanosecond instants elsewhere — a day has no time zone to lose.
 *
 * There is deliberately **no** foreign key on `accountId`: unlike a transaction, an account with only a
 * recurring transaction pointing at it (and no transaction of its own) can still be hard-deleted today, since
 * [com.kyovo.cents.application.usecase.DeleteAccountService] does not know about recurring transactions. Adding
 * a `RESTRICT` here would turn that into a raw database crash instead of the clean refusal a transaction
 * gets; the generation service already copes with a since-vanished account by skipping the rule
 * (`accountRepository.findById(rule.accountId) ?: continue`), so nothing breaks — a deleted account's rule
 * just lingers, unreachable, rather than being cleaned up. Revisit if that stale row ever needs cleaning up.
 *
 * `subcategoryId` mirrors `transactions`' own link: deleting a subcategory sets it to null here too
 * (`SET NULL`), the same rule as an existing transaction losing its subcategory.
 */
@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("subcategoryId")],
)
data class RecurringTransactionEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID,
    val category: String,
    val amount: Long,
    val title: String,
    val subcategoryId: UUID?,
    val description: String?,
    val frequency: String,
    val interval: Int,
    val startDate: Long,
    val endDate: Long?,
    val lastGeneratedDate: Long?,
)
