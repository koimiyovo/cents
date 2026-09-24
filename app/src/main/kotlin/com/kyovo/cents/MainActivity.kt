package com.kyovo.cents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kyovo.cents.ui.SplashScreen
import com.kyovo.cents.ui.account.AccountFormViewModel
import com.kyovo.cents.ui.home.HomeScreen
import com.kyovo.cents.ui.onboarding.OnboardingScreen
import com.kyovo.cents.ui.transaction.TransactionFormViewModel

// A plain enum (rather than a sealed interface of data objects) so rememberSaveable can persist
// it across configuration changes — Kotlin enums are Serializable for free, sealed-interface
// singletons aren't.
private enum class AppScreen { Splash, Onboarding, Home }

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as CentsApplication).appContainer }

    // Scoped to the Activity's ViewModelStore, which outlives rotations: the form being typed
    // survives them. The factory is needed because the ViewModel takes constructor arguments.
    private val formViewModel: TransactionFormViewModel by viewModels {
        viewModelFactory {
            initializer {
                TransactionFormViewModel(
                    recordTransaction = appContainer.recordTransaction,
                    recordTransfer = appContainer.recordTransfer,
                    dataRevision = appContainer.dataRevision,
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
                    dataRevision = appContainer.dataRevision,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.Splash) }

            when (screen) {
                AppScreen.Splash -> SplashScreen(onFinished = { screen = AppScreen.Onboarding })
                // Onboarding is shown on every launch for now — there is no "seen it already"
                // flag persisted yet.
                AppScreen.Onboarding -> OnboardingScreen(onFinished = { screen = AppScreen.Home })
                AppScreen.Home -> HomeScreen(
                    listAccounts = appContainer.listAccounts,
                    listArchivedAccounts = appContainer.listArchivedAccounts,
                    archiveAccount = appContainer.archiveAccount,
                    unarchiveAccount = appContainer.unarchiveAccount,
                    getAccount = appContainer.getAccount,
                    getAccountBalance = appContainer.getAccountBalance,
                    listTransactions = appContainer.listTransactions,
                    dataRevision = appContainer.dataRevision,
                    formViewModel = formViewModel,
                    accountFormViewModel = accountFormViewModel,
                )
            }
        }
    }
}
