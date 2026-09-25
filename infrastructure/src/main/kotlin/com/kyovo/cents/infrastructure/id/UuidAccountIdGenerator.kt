package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.output.AccountIdGenerator
import java.util.UUID

class UuidAccountIdGenerator : AccountIdGenerator
{
    override fun generate(): AccountId
    {
        return AccountId(UUID.randomUUID())
    }
}
