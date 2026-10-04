package com.kyovo.cents.ui.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.ui.common.AmountField
import com.kyovo.cents.ui.common.EMOJIS
import com.kyovo.cents.ui.common.ErrorText
import com.kyovo.cents.ui.common.NameAndEmojiField
import com.kyovo.cents.ui.common.SectionLabel
import com.kyovo.cents.ui.common.SubmitButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.DestructiveButton
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Bottom sheet to create or edit a project: a name, an optional emoji and an optional target. An edit also
 * offers the deletion ([onDelete] is null for a new project, where there is nothing to delete). Stateless,
 * like the other sheets: the form lives in a view model - [ProjectsViewModel] on the projects screen, the
 * transaction form's on its "new project" dialog - which is why it takes the form, not the view model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormSheet(
    form: ProjectFormState,
    errors: Set<ProjectFormError>,
    onFormChange: (ProjectFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.background,
        contentColor = palette.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(if (form.isEditing) R.string.project_form_title_edit else R.string.project_form_title_new),
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            NameAndEmojiField(
                palette = palette,
                name = form.name,
                // Typing stops at the domain's limit (see ProjectFormState.withName).
                onNameChange = { onFormChange(form.withName(it)) },
                placeholder = stringResource(R.string.project_form_name_placeholder),
                emoji = form.emoji,
                onEmojiChange = { onFormChange(form.withEmoji(it)) },
                emojis = EMOJIS,
                emojiDescription = stringResource(R.string.project_form_emoji_description),
                focusRequester = nameFocus,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(palette, stringResource(R.string.project_form_target_label))
                AmountField(
                    palette = palette,
                    value = form.targetText,
                    onValueChange = { onFormChange(form.withTarget(it)) },
                    error = ProjectFormError.TARGET_INVALID in errors,
                    textSize = 22.sp,
                )
                Text(
                    text = stringResource(R.string.project_form_target_hint),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
            }
            errors.forEach { error ->
                ErrorText(
                    palette = palette,
                    text = when (error)
                    {
                        ProjectFormError.NAME_REQUIRED  -> stringResource(R.string.project_form_error_name_required)
                        ProjectFormError.NAME_TOO_LONG  ->
                            stringResource(R.string.project_form_error_name_too_long, ProjectName.MAX_LENGTH)

                        ProjectFormError.EMOJI_INVALID  -> stringResource(R.string.project_form_error_emoji_invalid)
                        ProjectFormError.TARGET_INVALID -> stringResource(R.string.project_form_error_target_invalid)
                        ProjectFormError.NAME_TAKEN     -> stringResource(R.string.project_form_error_name_taken)
                        ProjectFormError.PROJECT_GONE   -> stringResource(R.string.project_form_error_gone)
                    },
                )
            }
            SubmitButton(
                palette,
                stringResource(if (form.isEditing) R.string.transaction_form_submit else R.string.project_form_create),
                onSubmit,
            )
            if (onDelete != null)
            {
                DestructiveButton(palette, stringResource(R.string.project_form_delete), onDelete)
            }
        }
    }
}
