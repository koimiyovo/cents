package com.kyovo.cents.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.IconTone

// One message per page: an icon, a short badge, a short title, and at most three rows of "title + a few
// words". No picture of the app's screens: they would have to be redrawn at every design change.

@Composable
fun OnboardingPage1(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageIcon(icon = "🔒", tone = IconTone.Green, palette = palette)
        Pill(text = stringResource(R.string.onboarding_page1_badge), palette = palette)
        PageTitle(text = stringResource(R.string.onboarding_page1_title), palette = palette)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(
                tone = IconTone.Green,
                title = stringResource(R.string.onboarding_page1_feature1_title),
                description = stringResource(R.string.onboarding_page1_feature1_description),
                palette = palette,
            )
            FeatureRow(
                tone = IconTone.Gold,
                title = stringResource(R.string.onboarding_page1_feature2_title),
                description = stringResource(R.string.onboarding_page1_feature2_description),
                palette = palette,
            )
            FeatureRow(
                tone = IconTone.Mint,
                title = stringResource(R.string.onboarding_page1_feature3_title),
                description = stringResource(R.string.onboarding_page1_feature3_description),
                palette = palette,
            )
        }
    }
}

@Composable
fun OnboardingPage2(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageIcon(icon = "🏦", tone = IconTone.Gold, palette = palette)
        Pill(text = stringResource(R.string.onboarding_page2_badge), palette = palette)
        PageTitle(text = stringResource(R.string.onboarding_page2_title), palette = palette)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureRow(
                tone = IconTone.Gold,
                title = stringResource(R.string.onboarding_page2_feature2_title),
                description = stringResource(R.string.onboarding_page2_feature2_description),
                palette = palette,
            )
            FeatureRow(
                tone = IconTone.Mint,
                title = stringResource(R.string.onboarding_page2_feature3_title),
                description = stringResource(R.string.onboarding_page2_feature3_description),
                palette = palette,
            )
        }
    }
}

@Composable
fun OnboardingPage3(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageIcon(icon = "✏️", tone = IconTone.Mint, palette = palette)
        Pill(text = stringResource(R.string.onboarding_page3_badge), palette = palette)
        PageTitle(text = stringResource(R.string.onboarding_page3_title), palette = palette)
        FeatureRow(
            tone = IconTone.Green,
            title = stringResource(R.string.onboarding_page3_feature1_title),
            description = stringResource(R.string.onboarding_page3_feature1_description),
            palette = palette,
        )
    }
}

@Composable
private fun PageTitle(text: String, palette: OnboardingPalette) {
    Text(
        text = text,
        color = palette.textPrimary,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
}
