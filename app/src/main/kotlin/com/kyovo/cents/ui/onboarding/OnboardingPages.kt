package com.kyovo.cents.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R

@Composable
fun OnboardingPage1(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill(text = stringResource(R.string.onboarding_page1_badge), palette = palette)
        Text(
            text = stringResource(R.string.onboarding_page1_title),
            color = palette.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_page1_subtitle),
            color = palette.textSecondary,
            fontSize = 15.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Pill(
            text = stringResource(R.string.onboarding_page1_rating),
            palette = palette,
            backgroundColor = palette.surfaceAlt,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun OnboardingPage2(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill(text = stringResource(R.string.onboarding_page2_badge), palette = palette)
        Text(
            text = stringResource(R.string.onboarding_page2_title),
            color = palette.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_page2_subtitle),
            color = palette.textSecondary,
            fontSize = 15.sp,
        )
        AccountsPreviewCard(palette)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(
                tone = IconTone.Mint,
                title = stringResource(R.string.onboarding_page2_feature1_title),
                description = stringResource(R.string.onboarding_page2_feature1_description),
                palette = palette,
            )
            FeatureRow(
                tone = IconTone.Gold,
                title = stringResource(R.string.onboarding_page2_feature2_title),
                description = stringResource(R.string.onboarding_page2_feature2_description),
                palette = palette,
            )
        }
    }
}

@Composable
fun OnboardingPage3(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pill(text = stringResource(R.string.onboarding_page3_badge), palette = palette)
        Text(
            text = stringResource(R.string.onboarding_page3_title),
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_page3_subtitle),
            color = palette.textSecondary,
            fontSize = 13.sp,
        )
        EntryPreviewCard(palette)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FeatureRow(
                tone = IconTone.Gold,
                title = stringResource(R.string.onboarding_page3_feature1_title),
                description = stringResource(R.string.onboarding_page3_feature1_description),
                palette = palette,
            )
            FeatureRow(
                tone = IconTone.Mint,
                title = stringResource(R.string.onboarding_page3_feature2_title),
                description = stringResource(R.string.onboarding_page3_feature2_description),
                palette = palette,
            )
        }
    }
}

@Composable
private fun AccountsPreviewCard(palette: OnboardingPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface),
    ) {
        AccountRow(
            tone = IconTone.Green,
            name = stringResource(R.string.onboarding_page2_account1_name),
            subtitle = stringResource(R.string.onboarding_page2_account1_subtitle),
            amount = stringResource(R.string.onboarding_page2_account1_amount),
            note = stringResource(R.string.onboarding_page2_account1_note),
            palette = palette,
        )
        HorizontalDivider(palette)
        AccountRow(
            tone = IconTone.Gold,
            name = stringResource(R.string.onboarding_page2_account2_name),
            subtitle = stringResource(R.string.onboarding_page2_account2_subtitle),
            amount = stringResource(R.string.onboarding_page2_account2_amount),
            note = stringResource(R.string.onboarding_page2_account2_note),
            palette = palette,
        )
    }
}

@Composable
private fun AccountRow(
    tone: IconTone,
    name: String,
    subtitle: String,
    amount: String,
    note: String,
    palette: OnboardingPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when (tone) {
                        IconTone.Green -> palette.iconToneGreen
                        IconTone.Gold -> palette.iconToneGold
                        IconTone.Mint -> palette.iconToneMint
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = palette.textMuted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = amount, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = note, color = palette.accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EntryPreviewCard(palette: OnboardingPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.onboarding_page3_entry_saved), color = palette.iconToneGreen, fontSize = 12.sp)
            Text(text = stringResource(R.string.onboarding_page3_entry_offline), color = palette.textMuted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.onboarding_page3_entry_label), color = palette.textMuted, fontSize = 12.sp)
            Text(
                text = stringResource(R.string.onboarding_page3_entry_amount),
                color = palette.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text(text = stringResource(R.string.onboarding_page3_category_label), color = palette.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip(stringResource(R.string.onboarding_page3_category_coffee), selected = false, palette = palette)
                CategoryChip(stringResource(R.string.onboarding_page3_category_groceries), selected = true, palette = palette)
                CategoryChip(stringResource(R.string.onboarding_page3_category_transport), selected = false, palette = palette)
                CategoryChip(stringResource(R.string.onboarding_page3_category_restaurant), selected = false, palette = palette)
            }
        }
        HorizontalDivider(palette)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(R.string.onboarding_page3_account_name), color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = stringResource(R.string.onboarding_page3_account_note), color = palette.textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, palette: OnboardingPalette) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.primaryButton else palette.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) palette.onPrimaryButton else palette.textSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HorizontalDivider(palette: OnboardingPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.divider),
    )
}
