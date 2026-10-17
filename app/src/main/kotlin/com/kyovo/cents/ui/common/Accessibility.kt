package com.kyovo.cents.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.kyovo.cents.R

// Screen-reader helpers (TalkBack) shared by the hand-built controls. Compose builds a semantics tree
// next to the UI; a custom `Box` + `clickable` is invisible to it as anything but "something tappable"
// until a role, a state or a description is added.

/** Hides a purely decorative node (an emoji repeated by the text beside it) from screen readers. */
internal fun Modifier.decorative(): Modifier = clearAndSetSemantics { }

/** Says "Déplié"/"Replié" for a control that folds a list, which a chevron alone shows only to sighted users. */
@Composable
internal fun Modifier.foldState(expanded: Boolean): Modifier
{
    val state = stringResource(if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed)
    return semantics { stateDescription = state }
}
