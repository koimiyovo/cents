package com.kyovo.cents.infrastructure.id

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.output.AccountIdGenerator
import kotlin.uuid.Uuid

class UuidAccountIdGenerator : AccountIdGenerator
{
    override fun generate(): AccountId
    {
        return AccountId(Uuid.random())
    }
}
