package com.kyovo.cents.ui.transaction

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyovo.cents.R
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * The "+" that opens the transaction form. Drawn rather than a Material icon, like the other
 * glyphs of this app (chevrons, eye), and filled with the same green as the selected chips.
 */
@Composable
fun AddTransactionFab(onClick: () -> Unit, modifier: Modifier = Modifier)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val description = stringResource(R.string.transaction_form_add_content_description)
    Canvas(
        modifier = modifier
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(palette.iconToneGreen)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .size(56.dp)
            .padding(18.dp),
    ) {
        val strokeWidth = size.width * 0.14f
        val color = palette.heroOnCardPrimary
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
