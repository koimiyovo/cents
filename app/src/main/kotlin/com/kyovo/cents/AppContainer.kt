package com.kyovo.cents

import com.kyovo.cents.application.usecase.ArchiveAccountService
import com.kyovo.cents.application.usecase.CreateSubcategoryService
import com.kyovo.cents.application.usecase.DeleteAccountService
import com.kyovo.cents.application.usecase.DeleteSubcategoryService
import com.kyovo.cents.application.usecase.DeleteTransactionService
import com.kyovo.cents.application.usecase.GetAccountBalanceService
import com.kyovo.cents.application.usecase.GetAccountService
import com.kyovo.cents.application.usecase.ListAccountsService
import com.kyovo.cents.application.usecase.ListArchivedAccountsService
import com.kyovo.cents.application.usecase.ListSubcategoriesService
import com.kyovo.cents.application.usecase.ListTransactionsService
import com.kyovo.cents.application.usecase.OpenAccountService
import com.kyovo.cents.application.usecase.RecordTransactionService
import com.kyovo.cents.application.usecase.RecordTransferService
import com.kyovo.cents.application.usecase.ReorderAccountsService
import com.kyovo.cents.application.usecase.UnarchiveAccountService
import com.kyovo.cents.application.usecase.UpdateAccountService
import com.kyovo.cents.application.usecase.UpdateInitialDepositService
import com.kyovo.cents.application.usecase.UpdateSubcategoryService
import com.kyovo.cents.application.usecase.UpdateTransactionService
import com.kyovo.cents.domain.port.input.ArchiveAccountUseCase
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.input.DeleteSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.ReorderAccountsUseCase
import com.kyovo.cents.domain.port.input.UnarchiveAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateInitialDepositUseCase
import com.kyovo.cents.domain.port.input.UpdateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.infrastructure.id.UuidAccountIdGenerator
import com.kyovo.cents.infrastructure.id.UuidSubcategoryIdGenerator
import com.kyovo.cents.infrastructure.id.UuidTransactionIdGenerator
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence
import java.time.Clock

/**
 * Manual wiring for the app's single-Activity shell: takes the storage (the Room database's
 * repositories and unit of work) and exposes the application services the UI reads from.
 * Held by [CentsApplication] so it survives Activity recreation; real DI can replace it later.
 */
class AppContainer(persistence: RoomPersistence) {
    private val accountRepository = persistence.accounts
    private val transactionRepository = persistence.transactions
    private val subcategoryRepository = persistence.subcategories
    private val transactionIdGenerator = UuidTransactionIdGenerator()
    private val unitOfWork = persistence.unitOfWork

    val listAccounts: ListAccountsUseCase = ListAccountsService(accountRepository)
    val listArchivedAccounts: ListArchivedAccountsUseCase = ListArchivedAccountsService(accountRepository)
    val archiveAccount: ArchiveAccountUseCase = ArchiveAccountService(accountRepository, Clock.systemUTC())
    val unarchiveAccount: UnarchiveAccountUseCase = UnarchiveAccountService(accountRepository)
    val updateAccount: UpdateAccountUseCase = UpdateAccountService(accountRepository)
    val deleteTransaction: DeleteTransactionUseCase = DeleteTransactionService(transactionRepository)
    val updateTransaction: UpdateTransactionUseCase = UpdateTransactionService(accountRepository, transactionRepository, subcategoryRepository)
    val updateInitialDeposit: UpdateInitialDepositUseCase = UpdateInitialDepositService(transactionRepository)
    val reorderAccounts: ReorderAccountsUseCase = ReorderAccountsService(accountRepository)
    val deleteAccount: DeleteAccountUseCase =
        DeleteAccountService(accountRepository, transactionRepository, unitOfWork)
    val getAccount: GetAccountUseCase = GetAccountService(accountRepository)
    val getAccountBalance: GetAccountBalanceUseCase =
        GetAccountBalanceService(accountRepository, transactionRepository)
    val listTransactions: ListTransactionsUseCase = ListTransactionsService(transactionRepository)
    val listSubcategories: ListSubcategoriesUseCase = ListSubcategoriesService(subcategoryRepository)
    val createSubcategory: CreateSubcategoryUseCase =
        CreateSubcategoryService(subcategoryRepository, UuidSubcategoryIdGenerator())
    val updateSubcategory: UpdateSubcategoryUseCase = UpdateSubcategoryService(subcategoryRepository)
    val deleteSubcategory: DeleteSubcategoryUseCase =
        DeleteSubcategoryService(subcategoryRepository, transactionRepository, unitOfWork)
    val openAccount: OpenAccountUseCase = OpenAccountService(
        accountRepository,
        UuidAccountIdGenerator(),
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
        Clock.systemUTC(),
    )
    val recordTransaction: RecordTransactionUseCase =
        RecordTransactionService(accountRepository, transactionRepository, transactionIdGenerator, subcategoryRepository)
    val recordTransfer: RecordTransferUseCase = RecordTransferService(
        accountRepository,
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
    )


    /**
     * Puts the demo data into an empty database (see [seedDemoDataIfEmpty]); a debug build asks for it
     * once at startup, off the main thread.
     */
    suspend fun seedDemoDataIfEmpty(): Boolean =
        com.kyovo.cents.data.seedDemoDataIfEmpty(accountRepository, transactionRepository, subcategoryRepository, unitOfWork)
}
