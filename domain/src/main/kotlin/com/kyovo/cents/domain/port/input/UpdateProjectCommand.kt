package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName

data class UpdateProjectCommand(
    val id: ProjectId,
    val name: ProjectName,
    val emoji: Emoji?,
    val target: Money?
)
