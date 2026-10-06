package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName

data class CreateProjectCommand(
    val name: ProjectName,
    val emoji: Emoji? = null,
    val target: Money? = null,
    val alertThreshold: AlertThreshold = AlertThreshold.DEFAULT
)
{
    fun toProject(id: ProjectId): Project
    {
        return Project(id, name, emoji, target, alertThreshold)
    }
}
