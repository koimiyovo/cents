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
import com.kyovo.cents.ui.common.ChevronDownIcon
import com.kyovo.cents.ui.common.IconTone

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
    }
}

@Composable
fun OnboardingPage2(palette: OnboardingPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Pill(text = stringResource(R.string.onboarding_page2_badge), palette = palette)
        Text(
            text = stringResource(R.string.onboarding_page2_title),
            color = palette.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        AccountsPreviewCard(palette)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pill(text = stringResource(R.string.onboarding_page3_badge), palette = palette)
        Text(
            text = stringResource(R.string.onboarding_page3_title),
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        EntryPreviewCard(palette)
    }
}

/**
 * A miniature of the real accounts screen: the consolidated balance (with the screen's own label), then
 * one row per account made of the account type's icon, its name and its balance — nothing else, since a
 * real row has nothing else.
 */
@Composable
private fun AccountsPreviewCard(palette: OnboardingPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = stringResource(R.string.accounts_hero_label), color = palette.textMuted, fontSize = 11.sp)
            Text(
                text = stringResource(R.string.onboarding_page2_total_amount),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(palette)
        AccountRow(
            tone = IconTone.Green,
            icon = "🏦",
            name = stringResource(R.string.onboarding_page2_account1_name),
            amount = stringResource(R.string.onboarding_page2_account1_amount),
            palette = palette,
        )
        HorizontalDivider(palette)
        AccountRow(
            tone = IconTone.Gold,
            icon = "💵",
            name = stringResource(R.string.onboarding_page2_account2_name),
            amount = stringResource(R.string.onboarding_page2_account2_amount),
            palette = palette,
        )
    }
}

@Composable
private fun AccountRow(
    tone: IconTone,
    icon: String,
    name: String,
    amount: String,
    palette: OnboardingPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (tone) {
                        IconTone.Green -> palette.iconToneGreen
                        IconTone.Gold -> palette.iconToneGold
                        IconTone.Mint -> palette.iconToneMint
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(text = amount, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * A miniature of the real "Nouvelle transaction" form, with the same fields in the same order and the
 * same labels: what the user sees here is what they will find when they add a transaction. (It shows
 * nothing the form does not do: no shortcut chips, no status the form does not have.)
 */
@Composable
private fun EntryPreviewCard(palette: OnboardingPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewChip(stringResource(R.string.onboarding_page3_type_expense), selected = true, palette = palette)
            PreviewChip(stringResource(R.string.onboarding_page3_type_income), selected = false, palette = palette)
            PreviewChip(stringResource(R.string.onboarding_page3_type_transfer), selected = false, palette = palette)
        }
        PreviewField(palette) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.onboarding_page3_entry_amount),
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = stringResource(R.string.onboarding_page3_entry_currency), color = palette.textSecondary, fontSize = 14.sp)
            }
        }
        PreviewField(palette) {
            Text(text = stringResource(R.string.onboarding_page3_entry_title), color = palette.textPrimary, fontSize = 14.sp)
        }
        PreviewDropdown(
            label = stringResource(R.string.onboarding_page3_subcategory_label),
            value = stringResource(R.string.onboarding_page3_subcategory_value),
            palette = palette,
        )
        PreviewDropdown(
            label = stringResource(R.string.onboarding_page3_account_label),
            value = stringResource(R.string.onboarding_page3_account_name),
            palette = palette,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(palette.primaryButton)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.onboarding_page3_save),
                color = palette.onPrimaryButton,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A rounded box like a text field of the form, holding [content]. */
@Composable
private fun PreviewField(palette: OnboardingPalette, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        content()
    }
}

/** A labelled field ending in a chevron, like the form's pick-one dropdowns. */
@Composable
private fun PreviewDropdown(label: String, value: String, palette: OnboardingPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, color = palette.textMuted, fontSize = 11.sp)
        PreviewField(palette) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = value, color = palette.textPrimary, fontSize = 14.sp)
                ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun PreviewChip(label: String, selected: Boolean, palette: OnboardingPalette) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.primaryButton else palette.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
