package com.kyovo.cents

import com.kyovo.cents.application.usecase.ArchiveAccountService
import com.kyovo.cents.application.usecase.DeleteAccountService
import com.kyovo.cents.application.usecase.DeleteTransactionService
import com.kyovo.cents.application.usecase.GetAccountBalanceService
import com.kyovo.cents.application.usecase.GetAccountService
import com.kyovo.cents.application.usecase.ListAccountsService
import com.kyovo.cents.application.usecase.ListArchivedAccountsService
import com.kyovo.cents.application.usecase.ListTransactionsService
import com.kyovo.cents.application.usecase.OpenAccountService
import com.kyovo.cents.application.usecase.RecordTransactionService
import com.kyovo.cents.application.usecase.RecordTransferService
import com.kyovo.cents.application.usecase.ReorderAccountsService
import com.kyovo.cents.application.usecase.UnarchiveAccountService
import com.kyovo.cents.application.usecase.UpdateAccountService
import com.kyovo.cents.application.usecase.UpdateTransactionService
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.data.seedHardcodedData
import com.kyovo.cents.domain.port.input.ArchiveAccountUseCase
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.ReorderAccountsUseCase
import com.kyovo.cents.domain.port.input.UnarchiveAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.infrastructure.id.UuidAccountIdGenerator
import com.kyovo.cents.infrastructure.id.UuidTransactionIdGenerator
import com.kyovo.cents.infrastructure.persistence.ListAccountRepository
import com.kyovo.cents.infrastructure.persistence.ListTransactionRepository
import com.kyovo.cents.infrastructure.persistence.ListUnitOfWork
import java.time.Clock

/**
 * Manual wiring for the app's current single-Activity shell: builds the in-memory repositories,
 * seeds them with fixed demo data, and exposes the application services the UI reads from.
 * Held by [CentsApplication] so it survives Activity recreation; real DI can replace it later.
 */
class AppContainer {
    private val accountRepository = ListAccountRepository()
    private val transactionRepository = ListTransactionRepository()
    private val transactionIdGenerator = UuidTransactionIdGenerator()
    private val unitOfWork = ListUnitOfWork(accountRepository, transactionRepository)

    val listAccounts: ListAccountsUseCase = ListAccountsService(accountRepository)
    val listArchivedAccounts: ListArchivedAccountsUseCase = ListArchivedAccountsService(accountRepository)
    val archiveAccount: ArchiveAccountUseCase = ArchiveAccountService(accountRepository, Clock.systemUTC())
    val unarchiveAccount: UnarchiveAccountUseCase = UnarchiveAccountService(accountRepository)
    val updateAccount: UpdateAccountUseCase = UpdateAccountService(accountRepository)
    val deleteTransaction: DeleteTransactionUseCase = DeleteTransactionService(transactionRepository)
    val updateTransaction: UpdateTransactionUseCase = UpdateTransactionService(accountRepository, transactionRepository)
    val reorderAccounts: ReorderAccountsUseCase = ReorderAccountsService(accountRepository)
    val deleteAccount: DeleteAccountUseCase =
        DeleteAccountService(accountRepository, transactionRepository, unitOfWork)
    val getAccount: GetAccountUseCase = GetAccountService(accountRepository)
    val getAccountBalance: GetAccountBalanceUseCase =
        GetAccountBalanceService(accountRepository, transactionRepository)
    val listTransactions: ListTransactionsUseCase = ListTransactionsService(transactionRepository)
    val openAccount: OpenAccountUseCase = OpenAccountService(
        accountRepository,
        UuidAccountIdGenerator(),
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
        Clock.systemUTC(),
    )
    val recordTransaction: RecordTransactionUseCase =
        RecordTransactionService(accountRepository, transactionRepository, transactionIdGenerator)
    val recordTransfer: RecordTransferUseCase = RecordTransferService(
        accountRepository,
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
    )

    /** Bumped after each write so the screens re-read (see [DataRevision]). */
    val dataRevision = DataRevision()

    init {
        seedHardcodedData(accountRepository, transactionRepository)
    }
}
