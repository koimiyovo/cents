package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.MainCurrencyRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The transactions as the totals see them (budgets, spending breakdown and trend, projects): every amount in the
 * main currency. A transaction of an account in another currency is converted with the rates kept on the device;
 * with no rate for it (none fetched yet, or a currency the rates do not cover) it is left out rather than added
 * at a wrong value. Everything else — saving, finding, deleting, `findAll` — is the real repository's, in the
 * original amounts.
 *
 * Until the user has chosen a main currency it is the euro. All amounts are converted at the latest rates, not
 * at the rate of the day they were spent.
 */
class ConvertedTransactionRepository(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val mainCurrencyRepository: MainCurrencyRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : TransactionRepository by transactionRepository
{
    override fun observeAll(): Flow<List<Transaction>>
    {
        return combine(
            transactionRepository.observeAll(),
            accountRepository.observeAll(),
            mainCurrencyRepository.observe().map { it ?: AccountCurrency.EUR },
            exchangeRateRepository.observe()
        ) { transactions, accounts, mainCurrency, rates ->
            val currencyOfAccount = accounts.associate { it.id to it.currency }
            transactions.mapNotNull { transaction ->
                val currency = currencyOfAccount[transaction.accountId] ?: return@mapNotNull null
                when
                {
                    currency == mainCurrency                          -> transaction
                    rates != null && rates.canConvert(currency, mainCurrency) ->
                        transaction.withAmount(rates.convert(transaction.amount, currency, mainCurrency))

                    else                                              -> null
                }
            }
        }.distinctUntilChanged()
    }
}
