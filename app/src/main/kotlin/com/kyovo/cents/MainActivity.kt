package com.kyovo.cents

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kyovo.cents.ui.SplashScreen
import com.kyovo.cents.ui.home.HomeScreen
import com.kyovo.cents.ui.onboarding.OnboardingScreen

private sealed interface AppScreen {
    data object Splash : AppScreen
    data object Onboarding : AppScreen
    data object Home : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

            when (screen) {
                AppScreen.Splash -> SplashScreen(onFinished = { screen = AppScreen.Onboarding })
                // Onboarding is shown on every launch for now — there is no "seen it already"
                // flag persisted yet.
                AppScreen.Onboarding -> OnboardingScreen(onFinished = { screen = AppScreen.Home })
                AppScreen.Home -> HomeScreen()
            }
        }
    }
}
