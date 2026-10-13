package com.kyovo.cents.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NOTE_DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)

/**
 * The line under a total that says which exchange rates it was converted with, or that it is incomplete (see
 * [RatesNote]). Nothing is drawn when no account is in another currency. [warning] is the colour of the lines that
 * need attention (old rates, a total that leaves accounts out), [normal] the one of the plain information.
 */
@Composable
fun RatesNoteText(note: RatesNote, normal: Color, warning: Color, modifier: Modifier = Modifier)
{
    val (text, color) = when (note)
    {
        RatesNote.None             -> return
        is RatesNote.RatesOf       -> stringResource(R.string.rates_note_of, note.day.format(NOTE_DAY_FORMAT)) to normal
        is RatesNote.Stale         -> stringResource(R.string.rates_note_stale, note.day.format(NOTE_DAY_FORMAT)) to warning
        is RatesNote.Incomplete    ->
            pluralStringResource(R.plurals.rates_note_incomplete, note.accounts, note.accounts) to warning
    }
    Text(text = text, color = color, fontSize = 12.sp, modifier = modifier.fillMaxWidth())
}
