package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A row of the `transactions` table: the amount in cents, the date in nanoseconds since 1970, the
 * category as the enum constant's name, and two links to other tables.
 *
 * The links are enforced by the database itself, a second line of defence behind the services:
 * - a transaction cannot point to an account that does not exist, and an account that still has
 *   transactions cannot be deleted (`RESTRICT`): the services delete the transactions first;
 * - deleting a subcategory leaves its transactions and empties their link (`SET NULL`), which is
 *   exactly the rule of the domain: a transaction just loses its subcategory.
 *
 * The indices make the lookups by account, by subcategory and by date fast once there are thousands of rows.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SubcategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["subcategoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("accountId"), Index("subcategoryId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID,
    val amount: Long,
    val title: String,
    val category: String,
    val subcategoryId: UUID?,
    val description: String?,
    val date: Long,
)
