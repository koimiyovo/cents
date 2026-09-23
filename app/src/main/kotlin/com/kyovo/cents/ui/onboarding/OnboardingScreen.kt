package com.kyovo.cents.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyovo.cents.R
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val palette = if (isSystemInDarkTheme()) DarkOnboardingPalette else LightOnboardingPalette
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingTopBar(palette = palette, onSkip = onFinished)
        Spacer(Modifier.height(16.dp))
        // A HorizontalPager lets each step be reached either by the buttons below or by
        // swiping, the same way a user would expect from any onboarding carousel.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> OnboardingPage1(palette)
                    1 -> OnboardingPage2(palette)
                    else -> OnboardingPage3(palette)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PageIndicator(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        OnboardingPrimaryButton(
            text = stringResource(buttonLabelFor(pagerState.currentPage)),
            onClick = {
                if (pagerState.currentPage == PAGE_COUNT - 1) {
                    onFinished()
                } else {
                    coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            palette = palette,
        )
        // Always laid out (just invisible on the first page) so the primary button above never
        // shifts position when this row appears or disappears.
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (pagerState.currentPage > 0) 1f else 0f),
            horizontalArrangement = Arrangement.Center,
        ) {
            OnboardingBackButton(
                onClick = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                palette = palette,
            )
        }
        Spacer(Modifier.height(6.dp))
        CaptionText(text = stringResource(captionFor(pagerState.currentPage)), palette = palette)
    }
}

private fun buttonLabelFor(pageIndex: Int) = when (pageIndex) {
    0 -> R.string.onboarding_page1_button
    1 -> R.string.onboarding_page2_button
    else -> R.string.onboarding_page3_button
}

private fun captionFor(pageIndex: Int) = when (pageIndex) {
    0 -> R.string.onboarding_page1_caption
    1 -> R.string.onboarding_page2_caption
    else -> R.string.onboarding_page3_caption
}
