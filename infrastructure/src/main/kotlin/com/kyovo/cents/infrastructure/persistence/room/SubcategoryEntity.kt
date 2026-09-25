package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A row of the `subcategories` table: plain values only, and nothing of the domain. The domain's
 * `Subcategory` (with its value classes and its enum) is converted from and to this by the mapper, so
 * the table can evolve without the domain noticing, and the domain never learns that Room exists.
 *
 * The kind is stored as the enum constant's name, as text, on purpose: Room could store the enum
 * itself, but then renaming a constant in the domain would silently change what the database means.
 */
@Entity(tableName = "subcategories")
data class SubcategoryEntity(
    @PrimaryKey val id: UUID,
    val kind: String,
    val name: String,
    val emoji: String?,
)
