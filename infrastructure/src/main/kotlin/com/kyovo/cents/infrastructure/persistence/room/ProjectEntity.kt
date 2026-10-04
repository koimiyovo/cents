package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

/**
 * A row of the `projects` table: the name, an optional emoji (opaque text) and an optional target in
 * cents (null when the project has none). The transactions point to it from their own `projectId` column.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: UUID,
    val name: String,
    val emoji: String?,
    val targetCents: Long?
)
