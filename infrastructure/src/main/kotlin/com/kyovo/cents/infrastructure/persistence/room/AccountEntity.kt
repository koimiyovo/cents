package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A row of the `accounts` table. Dates are nanoseconds since 1970 (see [toEpochNanos]), the type is
 * the enum constant's name and the currency its ISO code, all as plain values.
 *
 * `position` is the order the user gave the accounts by hand: it is stored, so it survives a restart,
 * and every listing is sorted by it. It belongs to the row, not to the domain's `Account`.
 */
@Entity(tableName = "accounts", indices = [Index("position")])
data class AccountEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val type: String,
    val currency: String,
    val createdAt: Long,
    val archivedAt: Long?,
    val description: String?,
    val position: Int,
)
