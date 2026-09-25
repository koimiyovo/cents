package com.kyovo.cents.ui.common

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.ui.home.AccountsPalette

/**
 * A one-line field showing the chosen emoji (or [noneLabel]) that unfolds a grid of [emojis] — the
 * same "one line, unfolds in place" pattern as [SelectDropdown], and like it it hides the keyboard so
 * the grid isn't covered. Tapping the chosen emoji again clears it; picking one folds the grid.
 *
 * Only the emojis this device can actually draw are offered: a recent emoji on an old Android
 * version renders as an empty box, which is a worse icon than none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmojiPickerField(
    palette: AccountsPalette,
    emojis: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    label: String,
    noneLabel: String,
    modifier: Modifier = Modifier,
)
{
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val drawable = remember(emojis) {
        val paint = Paint()
        emojis.filter { paint.hasGlyph(it) }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .clickable {
                    if (!expanded)
                    {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                    expanded = !expanded
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = palette.textMuted,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = selected ?: noneLabel,
                color = if (selected == null) palette.textMuted else palette.textPrimary,
                fontSize = if (selected == null) 15.sp else 22.sp,
            )
            Spacer(Modifier.width(8.dp))
            ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(14.dp))
        }
        if (expanded)
        {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                drawable.forEach { emoji ->
                    val isSelected = emoji == selected
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) palette.iconToneGreen else Color.Transparent)
                            .clickable {
                                expanded = false
                                onSelect(if (isSelected) null else emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
