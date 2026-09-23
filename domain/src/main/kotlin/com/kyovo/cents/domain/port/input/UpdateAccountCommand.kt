package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType

/**
 * Replaces the account's editable state. [description] has no default on purpose: the command
 * carries the whole new state, so `null` means "no description" and a caller has to say so, rather
 * than silently erasing an existing description by leaving the argument out.
 */
data class UpdateAccountCommand(
    val id: AccountId,
    val name: AccountName,
    val type: AccountType,
    val description: AccountDescription?
)
{
    fun toAccount(account: Account): Account
    {
        return account.copy(name = name, type = type, description = description)
    }
}