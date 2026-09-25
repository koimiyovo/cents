package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.port.output.SubcategoryIdGenerator
import java.util.UUID

class UuidSubcategoryIdGenerator : SubcategoryIdGenerator
{
    override fun generate(): SubcategoryId
    {
        return SubcategoryId(UUID.randomUUID())
    }
}
