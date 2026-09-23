package com.kyovo.cents

import com.kyovo.cents.application.usecase.GetAccountBalanceService
import com.kyovo.cents.application.usecase.GetAccountService
import com.kyovo.cents.application.usecase.ListAccountsService
import com.kyovo.cents.application.usecase.ListTransactionsService
import com.kyovo.cents.data.seedHardcodedData
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.infrastructure.persistence.ListAccountRepository
import com.kyovo.cents.infrastructure.persistence.ListTransactionRepository

/**
 * Manual wiring for the app's current single-Activity shell: builds the in-memory repositories,
 * seeds them with fixed demo data, and exposes the application services the UI reads from.
 * Held by [CentsApplication] so it survives Activity recreation; real DI can replace it later.
 */
class AppContainer {
    private val accountRepository = ListAccountRepository()
    private val transactionRepository = ListTransactionRepository()

    val listAccounts: ListAccountsUseCase = ListAccountsService(accountRepository)
    val getAccount: GetAccountUseCase = GetAccountService(accountRepository)
    val getAccountBalance: GetAccountBalanceUseCase =
        GetAccountBalanceService(accountRepository, transactionRepository)
    val listTransactions: ListTransactionsUseCase = ListTransactionsService(transactionRepository)

    init {
        seedHardcodedData(accountRepository, transactionRepository)
    }
}
