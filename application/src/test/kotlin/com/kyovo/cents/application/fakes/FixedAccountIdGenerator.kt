package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.output.AccountIdGenerator

class FixedAccountIdGenerator(private val id: AccountId) : AccountIdGenerator
{
    override fun generate(): AccountId = id
}
