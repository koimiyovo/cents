package com.kyovo.cents.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyovo.cents.R

/**
 * Stand-in for the real dashboard, only here so the onboarding flow has somewhere to navigate to.
 * Replace once accounts/transactions get their own Compose screens.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.home_placeholder))
    }
}
