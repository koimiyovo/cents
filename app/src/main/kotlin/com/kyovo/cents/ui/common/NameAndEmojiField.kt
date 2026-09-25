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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.ui.home.AccountsPalette

/**
 * A name and its emoji on one line: the emoji sits in a small button at the left of the name field,
 * the way a chat app puts an avatar next to a title. Tapping it unfolds a grid of [emojis] right
 * below the line — the same "unfolds in place" pattern as [SelectDropdown], and like it it hides the
 * keyboard so the grid isn't covered. Picking an emoji folds the grid; tapping the chosen one again
 * clears it (the button then shows a faint smiley: "an emoji can go here").
 *
 * Only the emojis this device can actually draw are offered: a recent emoji on an old Android
 * version renders as an empty box, which is a worse icon than none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NameAndEmojiField(
    palette: AccountsPalette,
    name: String,
    onNameChange: (String) -> Unit,
    placeholder: String,
    emoji: String?,
    onEmojiChange: (String?) -> Unit,
    emojis: List<String>,
    emojiDescription: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
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
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.background)
                    .clickable {
                        if (!expanded)
                        {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                        expanded = !expanded
                    }
                    .semantics { contentDescription = emojiDescription },
                contentAlignment = Alignment.Center,
            ) {
                // A faint smiley while none is chosen: it says an emoji can go here.
                Text(
                    text = emoji ?: "🙂",
                    fontSize = 22.sp,
                    modifier = Modifier.alpha(if (emoji == null) 0.35f else 1f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (name.isEmpty())
                {
                    Text(text = placeholder, color = palette.textMuted, fontSize = 15.sp)
                }
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(palette.textPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
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
                drawable.forEach { candidate ->
                    val isSelected = candidate == emoji
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) palette.iconToneGreen else Color.Transparent)
                            .clickable {
                                expanded = false
                                onEmojiChange(if (isSelected) null else candidate)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = candidate, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
