package com.kyovo.cents.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors for the accounts (home) screen, following the same pattern as the splash and onboarding
 * palettes: scoped to this screen rather than driving an app-wide Material 3 [androidx.compose.material3.ColorScheme].
 */
@Immutable
data class AccountsPalette(
    val background: Color,
    val kicker: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val surface: Color,
    val heroCardBackground: Color,
    val heroOnCardPrimary: Color,
    val heroOnCardSecondary: Color,
    val heroPillBackground: Color,
    val heroIncomeAccent: Color,
    val heroExpenseAccent: Color,
    val badgeBackground: Color,
    val badgeContent: Color,
    val iconToneGreen: Color,
    val iconToneGold: Color,
    val iconToneMint: Color,
    val primaryButtonBackground: Color,
    val primaryButtonBorder: Color,
    val primaryButtonContent: Color,
    val statusActiveColor: Color,
    val tipIconTone: Color,
    val divider: Color,
    val error: Color,
)

val LightAccountsPalette = AccountsPalette(
    background = Color(0xFFEAF3EC),
    kicker = Color(0xFF3F6F60),
    textPrimary = Color(0xFF16342A),
    textSecondary = Color(0xFF3D4A45),
    textMuted = Color(0xFF6B7B74),
    surface = Color(0xFFFFFFFF),
    heroCardBackground = Color(0xFF16342A),
    heroOnCardPrimary = Color(0xFFFFFFFF),
    heroOnCardSecondary = Color(0xFFBFD4C8),
    heroPillBackground = Color(0x33FFFFFF),
    heroIncomeAccent = Color(0xFF9BE8B4),
    heroExpenseAccent = Color(0xFFE9B26B),
    badgeBackground = Color(0xFFDCEAE0),
    badgeContent = Color(0xFF16342A),
    iconToneGreen = Color(0xFF1D6F63),
    iconToneGold = Color(0xFFD6982E),
    iconToneMint = Color(0xFFA9DFCB),
    primaryButtonBackground = Color(0xFFFFFFFF),
    primaryButtonBorder = Color(0xFF16342A),
    primaryButtonContent = Color(0xFF16342A),
    statusActiveColor = Color(0xFF1D6F63),
    tipIconTone = Color(0xFFD6982E),
    divider = Color(0xFFCBD9D0),
    error = Color(0xFFB3261E),
)

val DarkAccountsPalette = AccountsPalette(
    background = Color(0xFF0F1B16),
    kicker = Color(0xFFE3A83E),
    textPrimary = Color(0xFFF5F7F6),
    textSecondary = Color(0xFFC7D3CE),
    textMuted = Color(0xFF9FB3AB),
    surface = Color(0xFF17281F),
    heroCardBackground = Color(0xFF13241C),
    heroOnCardPrimary = Color(0xFFFFFFFF),
    heroOnCardSecondary = Color(0xFF9FB3AB),
    heroPillBackground = Color(0x331D6F63),
    heroIncomeAccent = Color(0xFF6FE39A),
    heroExpenseAccent = Color(0xFFE3A83E),
    badgeBackground = Color(0xFF1D3B30),
    badgeContent = Color(0xFFE3A83E),
    iconToneGreen = Color(0xFF1D6F63),
    iconToneGold = Color(0xFFB37F26),
    iconToneMint = Color(0xFF2A5346),
    primaryButtonBackground = Color(0xFF1D6F63),
    primaryButtonBorder = Color(0xFF1D6F63),
    primaryButtonContent = Color(0xFFFFFFFF),
    statusActiveColor = Color(0xFF6FE39A),
    tipIconTone = Color(0xFFE3A83E),
    divider = Color(0xFF23352C),
    error = Color(0xFFF2B8B5),
)
