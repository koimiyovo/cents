package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.ProjectId

interface ProjectIdGenerator
{
    fun generate(): ProjectId
}