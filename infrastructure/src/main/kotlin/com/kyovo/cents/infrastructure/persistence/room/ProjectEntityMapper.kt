package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.exception.InvalidAlertThresholdException
import com.kyovo.cents.domain.exception.InvalidProjectNameException
import com.kyovo.cents.domain.exception.InvalidProjectTargetException
import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName

fun Project.toEntity(): ProjectEntity
{
    return ProjectEntity(
        id = id.value,
        name = name.value,
        emoji = emoji?.value,
        targetCents = target?.value,
        alertPercent = alertThreshold.percent
    )
}

fun ProjectEntity.toDomain(): Project
{
    return try
    {
        Project(
            id = ProjectId(id),
            name = ProjectName(name),
            emoji = emoji?.let { Emoji(it) },
            target = targetCents?.let { Money(it) },
            alertThreshold = AlertThreshold(alertPercent)
        )
    } catch (e: InvalidProjectNameException)
    {
        throw IllegalStateException("Inconsistent project row in the database: $id (name)", e)
    } catch (e: InvalidProjectTargetException)
    {
        throw IllegalStateException("Inconsistent project row in the database: $id (target)", e)
    } catch (e: InvalidSubcategoryEmojiException)
    {
        throw IllegalStateException("Inconsistent project row in the database: $id (emoji)", e)
    } catch (e: InvalidAlertThresholdException)
    {
        throw IllegalStateException("Inconsistent project row in the database: $id (alert threshold)", e)
    }
}
