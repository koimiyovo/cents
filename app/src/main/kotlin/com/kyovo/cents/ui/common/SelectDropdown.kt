package com.kyovo.cents.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.ui.home.AccountsPalette

/** One entry of a [SelectDropdown]: [value] is what gets selected, [label] what the user reads. */
internal class SelectOption<T>(val value: T, val label: String)

/**
 * A single-choice picker that stays one line tall however many options there are — the alternative
 * to a row of chips once the list can grow (a dozen subcategories, many accounts). Tapping the
 * trigger unfolds the options right below it (same inline pattern as the period/account filters);
 * a long list is capped in height and scrolls inside itself, so the screen doesn't stretch.
 *
 * [fillWidth] picks the trigger's look: a pill for filters, a full-width field inside forms.
 *
 * Unfolding hides the keyboard: inside a form the amount field is focused and its keyboard would
 * cover the very options that were just unfolded.
 *
 * [footerLabel] adds an action after the options ("+ New ..."): it is not a value to select, so it
 * calls [onFooterClick] instead of [onSelect], and closes the list.
 */
@Composable
internal fun <T> SelectDropdown(
    palette: AccountsPalette,
    options: List<SelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    labelPrefix: String = "",
    placeholder: String = "",
    footerLabel: String? = null,
    onFooterClick: (() -> Unit)? = null,
)
{
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val selectedLabel = options.firstOrNull { it.value == selected }?.label

    Column(modifier = modifier) {
        val trigger = Modifier.clickable {
            if (!expanded)
            {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            expanded = !expanded
        }
        if (fillWidth)
        {
            DropdownField(selectedLabel ?: placeholder, selectedLabel == null, palette, trigger)
        } else
        {
            DropdownPill(labelPrefix + (selectedLabel ?: placeholder), palette, trigger)
        }
        if (expanded)
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .heightIn(max = MAX_OPTIONS_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    SelectableOptionRow(
                        label = option.label,
                        selected = option.value == selected,
                        palette = palette,
                        onClick = {
                            expanded = false
                            onSelect(option.value)
                        },
                    )
                }
                if (footerLabel != null && onFooterClick != null)
                {
                    Text(
                        text = footerLabel,
                        color = palette.kicker,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = false
                                onFooterClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// About six rows: enough to browse without the list swallowing the sheet or the screen.
private val MAX_OPTIONS_HEIGHT = 288.dp

/** A pill showing [label] with a hand-drawn chevron — not a "▾"/"⌄" glyph, whose vertical metrics
 *  vary across fonts and don't sit level with the label text (see EyeToggleIcon for the same
 *  reasoning). */
@Composable
internal fun DropdownPill(label: String, palette: AccountsPalette, modifier: Modifier = Modifier)
{
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(palette.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(12.dp))
    }
}

/** Form-width variant of [DropdownPill], sized like the other form fields. */
@Composable
private fun DropdownField(
    label: String,
    isPlaceholder: Boolean,
    palette: AccountsPalette,
    modifier: Modifier = Modifier,
)
{
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (isPlaceholder) palette.textMuted else palette.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        ChevronDownIcon(tint = palette.textSecondary, modifier = Modifier.size(14.dp))
    }
}

@Composable
internal fun ChevronDownIcon(tint: Color, modifier: Modifier = Modifier)
{
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.35f)
            lineTo(w * 0.5f, h * 0.75f)
            lineTo(w * 0.85f, h * 0.35f)
        }
        drawPath(
            path,
            color = tint,
            style = Stroke(width = w * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Same treatment as the subcategory chips: a solid fill, not just a text color change, so the
 *  current choice in a dropdown is unambiguous at a glance. */
@Composable
internal fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    palette: AccountsPalette,
    onClick: () -> Unit
)
{
    Text(
        text = label,
        color = if (selected) palette.heroOnCardPrimary else palette.textPrimary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.iconToneGreen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
