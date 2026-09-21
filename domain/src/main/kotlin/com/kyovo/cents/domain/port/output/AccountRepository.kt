package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.Account

interface AccountRepository
{
    fun save(account: Account)
}