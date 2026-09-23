package com.kyovo.cents.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors for the onboarding flow's brand illustration, mirroring the app's splash palette
 * (see [com.kyovo.cents.ui.theme.SplashPalette]): scoped to this flow rather than driving the
 * app-wide Material 3 color scheme, which will be designed once the rest of the app exists.
 */
@Immutable
data class OnboardingPalette(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val badgeBackground: Color,
    val badgeContent: Color,
    val iconToneGreen: Color,
    val iconToneGold: Color,
    val iconToneMint: Color,
    val iconContentOnGreen: Color,
    val iconContentOnGold: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val accent: Color,
    val dotInactive: Color,
    val skipButtonBackground: Color,
    val skipButtonContent: Color,
    val divider: Color,
)

val LightOnboardingPalette = OnboardingPalette(
    background = Color(0xFFEAF3EC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF0F5F1),
    textPrimary = Color(0xFF16342A),
    textSecondary = Color(0xFF3D4A45),
    textMuted = Color(0xFF6B7B74),
    badgeBackground = Color(0xFFDCEAE0),
    badgeContent = Color(0xFF16342A),
    iconToneGreen = Color(0xFF1D6F63),
    iconToneGold = Color(0xFFD6982E),
    iconToneMint = Color(0xFFA9DFCB),
    iconContentOnGreen = Color(0xFFFFFFFF),
    iconContentOnGold = Color(0xFFFFFFFF),
    primaryButton = Color(0xFF16342A),
    onPrimaryButton = Color(0xFFFFFFFF),
    accent = Color(0xFFD6982E),
    dotInactive = Color(0xFFCBD9D0),
    skipButtonBackground = Color(0xFFE3ECE5),
    skipButtonContent = Color(0xFF3D4A45),
    divider = Color(0xFFCBD9D0),
)

val DarkOnboardingPalette = OnboardingPalette(
    background = Color(0xFF0F1B16),
    surface = Color(0xFF17281F),
    surfaceAlt = Color(0xFF1D3B30),
    textPrimary = Color(0xFFF5F7F6),
    textSecondary = Color(0xFFC7D3CE),
    textMuted = Color(0xFF9FB3AB),
    badgeBackground = Color(0xFF1D3B30),
    badgeContent = Color(0xFFE3A83E),
    iconToneGreen = Color(0xFF1D6F63),
    iconToneGold = Color(0xFFB37F26),
    iconToneMint = Color(0xFF2A5346),
    iconContentOnGreen = Color(0xFFFFFFFF),
    iconContentOnGold = Color(0xFFFFFFFF),
    primaryButton = Color(0xFF1D6F63),
    onPrimaryButton = Color(0xFFFFFFFF),
    accent = Color(0xFFE3A83E),
    dotInactive = Color(0xFF2A3B33),
    skipButtonBackground = Color(0xFF1D3B30),
    skipButtonContent = Color(0xFFC7D3CE),
    divider = Color(0xFF23352C),
)
