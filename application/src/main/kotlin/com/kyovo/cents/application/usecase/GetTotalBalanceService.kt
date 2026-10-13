package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountBalance
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.TotalBalance
import com.kyovo.cents.domain.port.input.GetTotalBalanceUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.abs

class GetTotalBalanceService(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val mainCurrencyRepository: MainCurrencyRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : GetTotalBalanceUseCase
{
    override fun observe(): Flow<TotalBalance>
    {
        return combine(
            accountRepository.observeAll(),
            transactionRepository.observeAll(),
            mainCurrencyRepository.observe().map { it ?: AccountCurrency.EUR },
            exchangeRateRepository.observe()
        ) { accounts, transactions, mainCurrency, rates ->
            val balanceOfAccount = transactions.groupBy { it.accountId }
                .mapValues { (_, ofAccount) -> ofAccount.sumOf { it.signedAmount } }

            var total = 0L
            var unconverted = 0
            for (account in accounts.filter { it.archivedAt == null })
            {
                val balance = balanceOfAccount[account.id] ?: 0L
                when
                {
                    account.currency == mainCurrency -> total += balance
                    rates != null && rates.canConvert(account.currency, mainCurrency) ->
                    {
                        // A Money is never negative: convert the size of the balance, then give it its sign back.
                        val converted = rates.convert(Money(abs(balance)), account.currency, mainCurrency).value
                        total += if (balance < 0) -converted else converted
                    }

                    else                             -> unconverted++
                }
            }
            TotalBalance(AccountBalance(total), unconverted)
        }.distinctUntilChanged()
    }
}
