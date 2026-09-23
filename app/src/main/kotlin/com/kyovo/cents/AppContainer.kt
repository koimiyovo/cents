package com.kyovo.cents

import com.kyovo.cents.application.usecase.GetAccountBalanceService
import com.kyovo.cents.application.usecase.ListAccountsService
import com.kyovo.cents.application.usecase.ListTransactionsService
import com.kyovo.cents.data.seedHardcodedData
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.infrastructure.persistence.ListAccountRepository
import com.kyovo.cents.infrastructure.persistence.ListTransactionRepository

/**
 * Manual wiring for the app's current single-Activity shell: builds the in-memory repositories,
 * seeds them with fixed demo data, and exposes the application services the UI reads from.
 * Will move to the Application class (with real DI) once more than one screen needs it.
 */
class AppContainer {
    private val accountRepository = ListAccountRepository()
    private val transactionRepository = ListTransactionRepository()

    val listAccounts: ListAccountsUseCase = ListAccountsService(accountRepository)
    val getAccountBalance: GetAccountBalanceUseCase =
        GetAccountBalanceService(accountRepository, transactionRepository)
    val listTransactions: ListTransactionsUseCase = ListTransactionsService(transactionRepository)

    init {
        seedHardcodedData(accountRepository, transactionRepository)
    }
}
