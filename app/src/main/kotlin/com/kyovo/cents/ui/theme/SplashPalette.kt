package com.kyovo.cents.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors specific to the splash screen's brand illustration.
 *
 * Kept separate from the app-wide Material 3 [androidx.compose.material3.ColorScheme], which will
 * be designed once more screens exist: this palette only needs to reproduce one fixed piece of
 * brand art, not drive semantic component colors across the app.
 */
@Immutable
data class SplashPalette(
    val background: Color,
    val mark: Color,
    val markGlow: Color,
    val accent: Color,
    val wordmark: Color,
    val tagline: Color,
    val badgeBackground: Color,
    val badgeContent: Color,
    val progressTrack: Color,
    val caption: Color,
)

val LightSplashPalette = SplashPalette(
    background = Color(0xFFEAF3EC),
    mark = Color(0xFF16342A),
    markGlow = Color(0x33D6982E),
    accent = Color(0xFFD6982E),
    wordmark = Color(0xFF16342A),
    tagline = Color(0xFF4B5B55),
    badgeBackground = Color(0xFFDCEAE0),
    badgeContent = Color(0xFF16342A),
    progressTrack = Color(0xFFCBD9D0),
    caption = Color(0xFF6B7B74),
)

val DarkSplashPalette = SplashPalette(
    background = Color(0xFF0F1B16),
    mark = Color(0xFF1D3B30),
    markGlow = Color(0x40D6982E),
    accent = Color(0xFFE3A83E),
    wordmark = Color(0xFFF5F7F6),
    tagline = Color(0xFF9FB3AB),
    badgeBackground = Color(0xFF17281F),
    badgeContent = Color(0xFFE5EDE9),
    progressTrack = Color(0xFF23352C),
    caption = Color(0xFF7C8F86),
)
