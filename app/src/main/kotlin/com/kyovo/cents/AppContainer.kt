package com.kyovo.cents

import android.content.Context
import com.kyovo.cents.application.usecase.ArchiveAccountService
import com.kyovo.cents.application.usecase.CheckBudgetAlertsService
import com.kyovo.cents.application.usecase.CreateProjectService
import com.kyovo.cents.application.usecase.CreateRecurringTransactionService
import com.kyovo.cents.application.usecase.CreateSubcategoryService
import com.kyovo.cents.application.usecase.DeleteAccountService
import com.kyovo.cents.application.usecase.DeleteProjectService
import com.kyovo.cents.application.usecase.DeleteRecurringTransactionService
import com.kyovo.cents.application.usecase.DeleteSubcategoryService
import com.kyovo.cents.application.usecase.DeleteTransactionService
import com.kyovo.cents.application.usecase.GenerateRecurringTransactionsService
import com.kyovo.cents.application.usecase.GetAccountBalanceService
import com.kyovo.cents.application.usecase.GetAccountService
import com.kyovo.cents.application.usecase.GetBudgetProgressService
import com.kyovo.cents.application.usecase.GetProjectProgressService
import com.kyovo.cents.application.usecase.GetSpendingBreakdownService
import com.kyovo.cents.application.usecase.GetSpendingTrendService
import com.kyovo.cents.application.usecase.ListAccountsService
import com.kyovo.cents.application.usecase.ListArchivedAccountsService
import com.kyovo.cents.application.usecase.ListProjectsService
import com.kyovo.cents.application.usecase.ListRecurringTransactionsService
import com.kyovo.cents.application.usecase.ListSubcategoriesService
import com.kyovo.cents.application.usecase.ListTransactionsService
import com.kyovo.cents.application.usecase.NotifyBudgetAlertsService
import com.kyovo.cents.application.usecase.NotifyDueRecurringTransactionsService
import com.kyovo.cents.application.usecase.OpenAccountService
import com.kyovo.cents.application.usecase.RecordTransactionService
import com.kyovo.cents.application.usecase.RecordTransferService
import com.kyovo.cents.application.usecase.ReorderAccountsService
import com.kyovo.cents.application.usecase.SetBudgetService
import com.kyovo.cents.application.usecase.UnarchiveAccountService
import com.kyovo.cents.application.usecase.UpdateAccountService
import com.kyovo.cents.application.usecase.UpdateInitialDepositService
import com.kyovo.cents.application.usecase.UpdateProjectService
import com.kyovo.cents.application.usecase.UpdateRecurringTransactionService
import com.kyovo.cents.application.usecase.UpdateSubcategoryService
import com.kyovo.cents.application.usecase.UpdateTransactionService
import com.kyovo.cents.domain.port.input.ArchiveAccountUseCase
import com.kyovo.cents.domain.port.input.CheckBudgetAlertsUseCase
import com.kyovo.cents.domain.port.input.CreateProjectUseCase
import com.kyovo.cents.domain.port.input.CreateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteAccountUseCase
import com.kyovo.cents.domain.port.input.DeleteProjectUseCase
import com.kyovo.cents.domain.port.input.DeleteRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.DeleteSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteTransactionUseCase
import com.kyovo.cents.domain.port.input.GenerateRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.GetAccountUseCase
import com.kyovo.cents.domain.port.input.GetBudgetProgressUseCase
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.input.GetSpendingBreakdownUseCase
import com.kyovo.cents.domain.port.input.GetSpendingTrendUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListArchivedAccountsUseCase
import com.kyovo.cents.domain.port.input.ListProjectsUseCase
import com.kyovo.cents.domain.port.input.ListRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.domain.port.input.NotifyBudgetAlertUseCase
import com.kyovo.cents.domain.port.input.NotifyDueRecurringTransactionsUseCase
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import com.kyovo.cents.domain.port.input.ReorderAccountsUseCase
import com.kyovo.cents.domain.port.input.SetBudgetUseCase
import com.kyovo.cents.domain.port.input.UnarchiveAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateInitialDepositUseCase
import com.kyovo.cents.domain.port.input.UpdateProjectUseCase
import com.kyovo.cents.domain.port.input.UpdateRecurringTransactionUseCase
import com.kyovo.cents.domain.port.input.UpdateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.UpdateTransactionUseCase
import com.kyovo.cents.infrastructure.id.UuidAccountIdGenerator
import com.kyovo.cents.infrastructure.id.UuidProjectIdGenerator
import com.kyovo.cents.infrastructure.id.UuidRecurringTransactionIdGenerator
import com.kyovo.cents.infrastructure.id.UuidSubcategoryIdGenerator
import com.kyovo.cents.infrastructure.id.UuidTransactionIdGenerator
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence
import com.kyovo.cents.notification.SystemBudgetAlertNotifier
import com.kyovo.cents.notification.SystemRecurringTransactionNotifier
import java.time.Clock
import java.time.ZoneId

/**
 * Manual wiring for the app's single-Activity shell: takes the storage (the Room database's
 * repositories and unit of work) and exposes the application services the UI reads from.
 * Held by [CentsApplication] so it survives Activity recreation; real DI can replace it later.
 * [context] is only for [SystemBudgetAlertNotifier] (a notification channel, posting) — kept last and
 * unstored beyond that, everything else here works from the domain's ports alone.
 */
class AppContainer(context: Context, persistence: RoomPersistence)
{
    private val accountRepository = persistence.accounts
    private val transactionRepository = persistence.transactions
    private val subcategoryRepository = persistence.subcategories
    private val budgetRepository = persistence.budgets
    private val budgetAlertRepository = persistence.budgetAlerts
    private val recurringTransactionRepository = persistence.recurringTransactions
    private val projectRepository = persistence.projects
    private val transactionIdGenerator = UuidTransactionIdGenerator()
    private val unitOfWork = persistence.unitOfWork

    val listAccounts: ListAccountsUseCase = ListAccountsService(accountRepository)
    val listArchivedAccounts: ListArchivedAccountsUseCase =
        ListArchivedAccountsService(accountRepository)
    val archiveAccount: ArchiveAccountUseCase =
        ArchiveAccountService(accountRepository, Clock.systemUTC())
    val unarchiveAccount: UnarchiveAccountUseCase = UnarchiveAccountService(accountRepository)
    val updateAccount: UpdateAccountUseCase = UpdateAccountService(accountRepository)
    val deleteTransaction: DeleteTransactionUseCase =
        DeleteTransactionService(transactionRepository)
    val updateTransaction: UpdateTransactionUseCase =
        UpdateTransactionService(
            accountRepository,
            transactionRepository,
            subcategoryRepository,
            projectRepository
        )
    val updateInitialDeposit: UpdateInitialDepositUseCase =
        UpdateInitialDepositService(transactionRepository)
    val reorderAccounts: ReorderAccountsUseCase = ReorderAccountsService(accountRepository)
    val deleteAccount: DeleteAccountUseCase =
        DeleteAccountService(accountRepository, transactionRepository, unitOfWork)
    val getAccount: GetAccountUseCase = GetAccountService(accountRepository)
    val getAccountBalance: GetAccountBalanceUseCase =
        GetAccountBalanceService(accountRepository, transactionRepository)
    val listTransactions: ListTransactionsUseCase = ListTransactionsService(transactionRepository)
    val listSubcategories: ListSubcategoriesUseCase =
        ListSubcategoriesService(subcategoryRepository)
    val createSubcategory: CreateSubcategoryUseCase =
        CreateSubcategoryService(subcategoryRepository, UuidSubcategoryIdGenerator())
    val updateSubcategory: UpdateSubcategoryUseCase =
        UpdateSubcategoryService(subcategoryRepository)
    val deleteSubcategory: DeleteSubcategoryUseCase =
        DeleteSubcategoryService(
            subcategoryRepository,
            transactionRepository,
            budgetRepository,
            budgetAlertRepository,
            unitOfWork,
        )
    val setBudget: SetBudgetUseCase = SetBudgetService(budgetRepository, subcategoryRepository)

    // A month runs from midnight to midnight in the zone the transaction form dates things in (the
    // device's), so an expense lands in the same month for both.
    val getBudgetProgress: GetBudgetProgressUseCase =
        GetBudgetProgressService(budgetRepository, transactionRepository, ZoneId.systemDefault())
    val checkBudgetAlerts: CheckBudgetAlertsUseCase =
        CheckBudgetAlertsService(getBudgetProgress, budgetAlertRepository)
    val notifyBudgetAlerts: NotifyBudgetAlertUseCase =
        NotifyBudgetAlertsService(
            checkBudgetAlerts,
            SystemBudgetAlertNotifier(context, subcategoryRepository)
        )
    val getSpendingBreakdown: GetSpendingBreakdownUseCase =
        GetSpendingBreakdownService(transactionRepository, ZoneId.systemDefault())
    val getSpendingTrend: GetSpendingTrendUseCase =
        GetSpendingTrendService(transactionRepository, ZoneId.systemDefault())
    val openAccount: OpenAccountUseCase = OpenAccountService(
        accountRepository,
        UuidAccountIdGenerator(),
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
        Clock.systemUTC(),
    )
    val recordTransaction: RecordTransactionUseCase =
        RecordTransactionService(
            accountRepository,
            transactionRepository,
            transactionIdGenerator,
            projectRepository,
            subcategoryRepository,
        )
    val recordTransfer: RecordTransferUseCase = RecordTransferService(
        accountRepository,
        transactionRepository,
        transactionIdGenerator,
        unitOfWork,
    )

    val listProjects: ListProjectsUseCase = ListProjectsService(projectRepository)
    val createProject: CreateProjectUseCase =
        CreateProjectService(projectRepository, UuidProjectIdGenerator())
    val updateProject: UpdateProjectUseCase = UpdateProjectService(projectRepository)
    val deleteProject: DeleteProjectUseCase =
        DeleteProjectService(projectRepository, transactionRepository, unitOfWork)
    val getProjectProgress: GetProjectProgressUseCase =
        GetProjectProgressService(projectRepository, transactionRepository)

    val listRecurringTransactions: ListRecurringTransactionsUseCase =
        ListRecurringTransactionsService(recurringTransactionRepository)
    val createRecurringTransaction: CreateRecurringTransactionUseCase =
        CreateRecurringTransactionService(
            recurringTransactionRepository,
            accountRepository,
            subcategoryRepository,
            UuidRecurringTransactionIdGenerator()
        )
    val updateRecurringTransaction: UpdateRecurringTransactionUseCase =
        UpdateRecurringTransactionService(recurringTransactionRepository, subcategoryRepository)
    val deleteRecurringTransaction: DeleteRecurringTransactionUseCase =
        DeleteRecurringTransactionService(recurringTransactionRepository)

    // Uses the device's own time zone, like the budgets: a rule's due date is a calendar day where the
    // user lives, not in UTC.
    val generateRecurringTransactions: GenerateRecurringTransactionsUseCase =
        GenerateRecurringTransactionsService(
            recurringTransactionRepository,
            transactionRepository,
            accountRepository,
            subcategoryRepository,
            transactionIdGenerator,
            unitOfWork,
            Clock.system(ZoneId.systemDefault()),
            notifyBudgetAlerts,
        )
    val notifyDueRecurringTransactions: NotifyDueRecurringTransactionsUseCase =
        NotifyDueRecurringTransactionsService(
            recurringTransactionRepository,
            accountRepository,
            SystemRecurringTransactionNotifier(context)
        )
}
