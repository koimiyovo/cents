package com.kyovo.cents.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.ui.common.IconTone

private fun OnboardingPalette.toneBackground(tone: IconTone): Color =
    when (tone) {
        IconTone.Green -> iconToneGreen
        IconTone.Gold -> iconToneGold
        IconTone.Mint -> iconToneMint
    }

@Composable
fun OnboardingTopBar(
    palette: OnboardingPalette,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.textPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "¢", color = palette.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.skipButtonBackground)
                .clickable(onClick = onSkip)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_skip),
                color = palette.skipButtonContent,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
    contentColor: Color = palette.badgeContent,
    backgroundColor: Color = palette.badgeBackground,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeatureRow(
    tone: IconTone,
    title: String,
    description: String,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .padding(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.toneBackground(tone)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = palette.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 24.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) palette.accent else palette.dotInactive),
            )
        }
    }
}

@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.primaryButton)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = palette.onPrimaryButton,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun OnboardingBackButton(
    onClick: () -> Unit,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.onboarding_back),
        color = palette.textMuted,
        fontSize = 14.sp,
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
fun CaptionText(
    text: String,
    palette: OnboardingPalette,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
