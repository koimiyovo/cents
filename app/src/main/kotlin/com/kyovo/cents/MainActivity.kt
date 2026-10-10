package com.kyovo.cents

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kyovo.cents.notification.NotificationTarget
import com.kyovo.cents.ui.SplashScreen
import com.kyovo.cents.ui.account.AccountFormViewModel
import com.kyovo.cents.ui.backup.AutomaticBackupViewModel
import com.kyovo.cents.ui.backup.DataBackupViewModel
import com.kyovo.cents.ui.budget.BudgetCycleViewModel
import com.kyovo.cents.ui.budget.BudgetsViewModel
import com.kyovo.cents.ui.home.HomeScreen
import com.kyovo.cents.ui.onboarding.OnboardingScreen
import com.kyovo.cents.ui.onboarding.OnboardingSeen
import com.kyovo.cents.ui.onboarding.onboardingDataStore
import com.kyovo.cents.ui.project.ProjectsViewModel
import com.kyovo.cents.ui.recurring.RecurringTransactionsViewModel
import com.kyovo.cents.ui.subcategory.SubcategoriesViewModel
import com.kyovo.cents.ui.transaction.InitialDepositFormViewModel
import com.kyovo.cents.ui.transaction.TransactionFormViewModel
import kotlinx.coroutines.launch

// A plain enum (rather than a sealed interface of data objects) so rememberSaveable can persist
// it across configuration changes — Kotlin enums are Serializable for free, sealed-interface
// singletons aren't.
private enum class AppScreen { Splash, Onboarding, Home }

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as CentsApplication).appContainer }

    private val onboardingSeen by lazy { OnboardingSeen(applicationContext.onboardingDataStore) }

    // The tab a tapped notification asked for, until the home screen has shown it. Read from the launching
    // intent (only the first time: after a rotation the same intent would navigate again) and from
    // onNewIntent when the app was already running.
    private var notificationTarget by mutableStateOf<NotificationTarget?>(null)

    // Scoped to the Activity's ViewModelStore, which outlives rotations: the form being typed
    // survives them. The factory is needed because the ViewModel takes constructor arguments.
    private val formViewModel: TransactionFormViewModel by viewModels {
        viewModelFactory {
            initializer {
                TransactionFormViewModel(
                    recordTransaction = appContainer.recordTransaction,
                    recordTransfer = appContainer.recordTransfer,
                    updateTransaction = appContainer.updateTransaction,
                    deleteTransaction = appContainer.deleteTransaction,
                    createSubcategory = appContainer.createSubcategory,
                    checkBudgetAlerts = appContainer.checkBudgetAlerts,
                    createRecurringTransaction = appContainer.createRecurringTransaction,
                    generateRecurringTransactions = appContainer.generateRecurringTransactions,
                    createProject = appContainer.createProject,
                    getProjectProgress = appContainer.getProjectProgress,
                    getBudgetCalendar = appContainer.getBudgetCalendar,
                )
            }
        }
    }

    private val projectsViewModel: ProjectsViewModel by viewModels {
        viewModelFactory {
            initializer {
                ProjectsViewModel(
                    createProject = appContainer.createProject,
                    updateProject = appContainer.updateProject,
                    deleteProject = appContainer.deleteProject,
                )
            }
        }
    }

    private val initialDepositFormViewModel: InitialDepositFormViewModel by viewModels {
        viewModelFactory {
            initializer {
                InitialDepositFormViewModel(
                    updateInitialDeposit = appContainer.updateInitialDeposit,
                )
            }
        }
    }

    private val subcategoriesViewModel: SubcategoriesViewModel by viewModels {
        viewModelFactory {
            initializer {
                SubcategoriesViewModel(
                    createSubcategory = appContainer.createSubcategory,
                    updateSubcategory = appContainer.updateSubcategory,
                    deleteSubcategory = appContainer.deleteSubcategory,
                )
            }
        }
    }

    // Uses the device's time zone, like the budgets themselves (see AppContainer), so the month it shows
    // is the month the budget progress is worked out for.
    private val budgetsViewModel: BudgetsViewModel by viewModels {
        viewModelFactory {
            initializer {
                BudgetsViewModel(
                    listSubcategories = appContainer.listSubcategories,
                    getBudgetProgress = appContainer.getBudgetProgress,
                    getSpendingBreakdown = appContainer.getSpendingBreakdown,
                    getSpendingTrend = appContainer.getSpendingTrend,
                    getBudgetCalendar = appContainer.getBudgetCalendar,
                    setBudget = appContainer.setBudget,
                )
            }
        }
    }

    private val budgetCycleViewModel: BudgetCycleViewModel by viewModels {
        viewModelFactory {
            initializer {
                BudgetCycleViewModel(
                    getBudgetCalendar = appContainer.getBudgetCalendar,
                    setDefaultStartDay = appContainer.setDefaultBudgetStartDay,
                    setCycleStart = appContainer.setBudgetCycleStart,
                    clearCycleStart = appContainer.clearBudgetCycleStart,
                )
            }
        }
    }

    private val recurringTransactionsViewModel: RecurringTransactionsViewModel by viewModels {
        viewModelFactory {
            initializer {
                RecurringTransactionsViewModel(
                    createRecurringTransaction = appContainer.createRecurringTransaction,
                    updateRecurringTransaction = appContainer.updateRecurringTransaction,
                    deleteRecurringTransaction = appContainer.deleteRecurringTransaction,
                    generateRecurringTransactions = appContainer.generateRecurringTransactions,
                )
            }
        }
    }

    private val automaticBackupViewModel: AutomaticBackupViewModel by viewModels {
        viewModelFactory {
            initializer {
                AutomaticBackupViewModel(appContainer.automaticBackupSettings, appContainer.runAutomaticBackup)
            }
        }
    }

    private val dataBackupViewModel: DataBackupViewModel by viewModels {
        viewModelFactory {
            initializer {
                DataBackupViewModel(
                    exportData = appContainer.exportData,
                    importData = appContainer.importData,
                    files = appContainer.backupFiles,
                    exportTransactionsCsv = appContainer.exportTransactionsCsv,
                )
            }
        }
    }

    private val accountFormViewModel: AccountFormViewModel by viewModels {
        viewModelFactory {
            initializer {
                AccountFormViewModel(
                    openAccount = appContainer.openAccount,
                    updateAccount = appContainer.updateAccount,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null)
        {
            notificationTarget = targetOf(intent)
        }
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.Splash) }
            val scope = rememberCoroutineScope()

            when (screen) {
                // The splash is the wait: the flag is read when it ends, so no extra loading state.
                AppScreen.Splash -> SplashScreen(onFinished = {
                    scope.launch {
                        screen = if (onboardingSeen.isSeen()) AppScreen.Home else AppScreen.Onboarding
                    }
                })
                AppScreen.Onboarding -> OnboardingScreen(onFinished = {
                    scope.launch {
                        onboardingSeen.markSeen()
                        screen = AppScreen.Home
                    }
                })
                AppScreen.Home -> HomeScreen(
                    listAccounts = appContainer.listAccounts,
                    listArchivedAccounts = appContainer.listArchivedAccounts,
                    archiveAccount = appContainer.archiveAccount,
                    unarchiveAccount = appContainer.unarchiveAccount,
                    deleteAccount = appContainer.deleteAccount,
                    reorderAccounts = appContainer.reorderAccounts,
                    getAccount = appContainer.getAccount,
                    getAccountBalance = appContainer.getAccountBalance,
                    listTransactions = appContainer.listTransactions,
                    listSubcategories = appContainer.listSubcategories,
                    listRecurringTransactions = appContainer.listRecurringTransactions,
                    listProjects = appContainer.listProjects,
                    getProjectProgress = appContainer.getProjectProgress,
                    formViewModel = formViewModel,
                    initialDepositFormViewModel = initialDepositFormViewModel,
                    accountFormViewModel = accountFormViewModel,
                    subcategoriesViewModel = subcategoriesViewModel,
                    budgetsViewModel = budgetsViewModel,
                    budgetCycleViewModel = budgetCycleViewModel,
                    recurringTransactionsViewModel = recurringTransactionsViewModel,
                    projectsViewModel = projectsViewModel,
                    dataBackupViewModel = dataBackupViewModel,
                    automaticBackupViewModel = automaticBackupViewModel,
                    notificationTarget = notificationTarget,
                    onNotificationTargetHandled = { notificationTarget = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent)
    {
        super.onNewIntent(intent)
        notificationTarget = targetOf(intent)
    }

    private fun targetOf(intent: Intent?): NotificationTarget?
    {
        return NotificationTarget.from(intent?.getStringExtra(NotificationTarget.EXTRA))
    }
}
