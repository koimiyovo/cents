package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.AccountId

interface AccountIdGenerator
{
    fun generate(): AccountId
}