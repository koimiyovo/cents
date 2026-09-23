package com.kyovo.cents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kyovo.cents.ui.SplashScreen
import com.kyovo.cents.ui.home.HomeScreen
import com.kyovo.cents.ui.onboarding.OnboardingScreen

// A plain enum (rather than a sealed interface of data objects) so rememberSaveable can persist
// it across configuration changes — Kotlin enums are Serializable for free, sealed-interface
// singletons aren't.
private enum class AppScreen { Splash, Onboarding, Home }

class MainActivity : ComponentActivity() {
    private val appContainer = AppContainer()

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
                    getAccountBalance = appContainer.getAccountBalance,
                    listTransactions = appContainer.listTransactions,
                )
            }
        }
    }
}
