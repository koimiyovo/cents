package com.kyovo.cents.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.BuildConfig
import com.kyovo.cents.R
import com.kyovo.cents.ui.theme.DarkSplashPalette
import com.kyovo.cents.ui.theme.LightSplashPalette
import com.kyovo.cents.ui.theme.SplashPalette
import kotlinx.coroutines.delay

private const val SPLASH_DISPLAY_DURATION_MS = 1500L

@Composable
fun SplashScreen(onFinished: () -> Unit = {}, modifier: Modifier = Modifier) {
    val palette = if (isSystemInDarkTheme()) DarkSplashPalette else LightSplashPalette

    LaunchedEffect(Unit) {
        delay(SPLASH_DISPLAY_DURATION_MS)
        onFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoinMark(palette)
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = palette.wordmark,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = palette.tagline,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(20.dp))
            PrivacyBadge(palette)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50)),
                color = palette.accent,
                trackColor = palette.progressTrack,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.splash_status),
                    color = palette.caption,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.splash_version, BuildConfig.VERSION_NAME),
                    color = palette.caption,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun CoinMark(palette: SplashPalette) {
    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(palette.markGlow),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(palette.mark),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.matchParentSize().padding(6.dp)) {
                drawCircle(
                    color = palette.accent,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    ),
                )
            }
            Text(
                text = "¢",
                color = palette.accent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PrivacyBadge(palette: SplashPalette) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.badgeBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = palette.badgeContent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.splash_privacy_badge),
            color = palette.badgeContent,
            fontSize = 13.sp,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SplashScreenLightPreview() {
    SplashScreen()
}

@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF0F1B16)
@Composable
private fun SplashScreenDarkPreview() {
    SplashScreen()
}
