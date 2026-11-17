package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.ExchangeRates
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ExchangeRateRepository
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.LocalDate

/**
 * Every project with its transactions, in the order of the projects, each amount in that project's currency: as it
 * is when the account has the same currency, otherwise converted with the rates kept on the device (all at the latest
 * rates, like the other totals). Before any rate is fetched the euro and the CFA franc still convert, being tied by
 * treaty. A transaction that cannot be converted, or whose account is unknown, is left out rather than counted at a
 * wrong value. Shared by the progress of the projects and their analysis, so both add up the same amounts.
 */
internal class ProjectTransactions(
    private val projectRepository: ProjectRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val exchangeRateRepository: ExchangeRateRepository
)
{
    fun observe(): Flow<Map<Project, List<Transaction>>>
    {
        return combine(
            projectRepository.observeAll(),
            transactionRepository.observeAll(),
            accountRepository.observeAll(),
            exchangeRateRepository.observe()
        ) { projects, transactions, accounts, kept ->
            val rates = kept ?: ExchangeRates(LocalDate.ofEpochDay(0), emptyMap())
            val currencyOfAccount = accounts.associate { it.id to it.currency }
            val byProject = transactions.filter { it.projectId != null }.groupBy { it.projectId }
            projects.associateWith { project ->
                byProject[project.id].orEmpty().mapNotNull { transaction ->
                    val currency = currencyOfAccount[transaction.accountId] ?: return@mapNotNull null
                    if (!rates.canConvert(currency, project.currency)) return@mapNotNull null
                    transaction.withAmount(rates.convert(transaction.amount, currency, project.currency))
                }
            }
        }.distinctUntilChanged()
    }
}
