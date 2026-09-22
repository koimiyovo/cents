package com.kyovo.cents.application.fakes

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Currency
import kotlin.uuid.Uuid

fun anAccountId(value: String = "11111111-1111-1111-1111-111111111111"): AccountId
{
    return AccountId(Uuid.parse(value))
}

fun aCurrency(code: String = "EUR"): AccountCurrency
{
    return AccountCurrency(Currency.getInstance(code))
}

fun anInstant(value: String = "2026-09-22T10:00:00Z"): Instant
{
    return Instant.parse(value)
}

fun aClock(instant: Instant = anInstant()): Clock
{
    return Clock.fixed(instant, ZoneOffset.UTC)
}

fun anAccount(
    id: AccountId = anAccountId(),
    name: AccountName = AccountName("Livret A"),
    type: AccountType = AccountType.CHECKING,
    currency: AccountCurrency = aCurrency(),
    createdAt: Instant = anInstant()
): Account
{
    return Account(id, name, type, currency, createdAt)
}

fun anOpenAccountCommand(
    name: AccountName = AccountName("Livret A"),
    type: AccountType = AccountType.CHECKING,
    currency: AccountCurrency = aCurrency(),
    initialAmount: Money = aMoney()
): OpenAccountCommand
{
    return OpenAccountCommand(name, type, currency, initialAmount)
}
