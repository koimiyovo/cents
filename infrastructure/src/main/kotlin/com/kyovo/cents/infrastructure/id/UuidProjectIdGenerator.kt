package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.port.output.ProjectIdGenerator
import java.util.UUID

class UuidProjectIdGenerator : ProjectIdGenerator
{
    override fun generate(): ProjectId
    {
        return ProjectId(UUID.randomUUID())
    }
}
