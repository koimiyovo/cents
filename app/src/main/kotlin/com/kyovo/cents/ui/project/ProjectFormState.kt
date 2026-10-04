package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.UpdateProjectCommand
import com.kyovo.cents.ui.budget.THRESHOLD_SLIDER_MAX_PERCENT
import com.kyovo.cents.ui.budget.THRESHOLD_SLIDER_MIN_PERCENT
import com.kyovo.cents.ui.budget.THRESHOLD_SLIDER_STEP_PERCENT
import com.kyovo.cents.ui.common.acceptsAmountInput
import com.kyovo.cents.ui.common.formatCentsForInput
import com.kyovo.cents.ui.common.limitNameInput
import com.kyovo.cents.ui.common.parseAmountToCents

/** Why a project can't be saved. The first four are found by the form, the others by the use cases. */
enum class ProjectFormError
{
    NAME_REQUIRED,
    NAME_TOO_LONG,
    EMOJI_INVALID,

    /** The target is typed but is not a positive amount. */
    TARGET_INVALID,

    /** Another project already has that name (case, spaces and accents ignored). */
    NAME_TAKEN,

    /** Editing: the project is gone since the form was opened. */
    PROJECT_GONE,
}

sealed interface ProjectSubmission
{
    data class Create(val command: CreateProjectCommand) : ProjectSubmission
    data class Update(val command: UpdateProjectCommand) : ProjectSubmission
    data class Invalid(val errors: Set<ProjectFormError>) : ProjectSubmission
}

/**
 * The form to create or edit a project, as raw text: what is typed is parsed in [submit], like the
 * other forms. The target is typed like an amount (at most two decimals) and may be left blank: a project
 * without a target is only followed, not measured. An edit carries the whole new state, so blanking the
 * target or taking the emoji away removes it. The alert threshold, in percent, is moved with the same slider as a
 * budget's (from half the target to the target itself, in steps of five) and only means something with a target.
 */
data class ProjectFormState(
    val name: String = "",
    val emoji: String? = null,
    val targetText: String = "",
    val thresholdPercent: Int = AlertThreshold.DEFAULT.percent,
    /** Set when an existing project is being edited instead of a new one created. */
    val editingId: ProjectId? = null,
)
{
    val isEditing: Boolean get() = editingId != null

    companion object
    {
        fun creating(): ProjectFormState = ProjectFormState()

        fun editing(project: Project): ProjectFormState = ProjectFormState(
            name = project.name.value,
            emoji = project.emoji?.value,
            targetText = project.target?.let { formatCentsForInput(it.value) }.orEmpty(),
            thresholdPercent = project.alertThreshold.percent,
            editingId = project.id,
        )
    }

    /** The name after an edit of the field: cut at the domain's limit, like every name field. */
    fun withName(text: String): ProjectFormState = copy(name = limitNameInput(text, ProjectName.MAX_LENGTH))

    /** The emoji picked; null removes it. */
    fun withEmoji(emoji: String?): ProjectFormState = copy(emoji = emoji)

    /** An edit that would break the shape of an amount (letters, a third decimal...) is dropped. */
    fun withTarget(text: String): ProjectFormState
    {
        if (!acceptsAmountInput(text)) return this
        return copy(targetText = text)
    }

    /**
     * The slider moved to [percent]: brought to the nearest step of the slider and kept within its range, so
     * the form only ever holds a position the slider can show.
     */
    fun withThreshold(percent: Int): ProjectFormState
    {
        val step = THRESHOLD_SLIDER_STEP_PERCENT
        val snapped = Math.round(percent.toDouble() / step).toInt() * step
        return copy(thresholdPercent = snapped.coerceIn(THRESHOLD_SLIDER_MIN_PERCENT, THRESHOLD_SLIDER_MAX_PERCENT))
    }

    fun submit(): ProjectSubmission
    {
        val errors = mutableSetOf<ProjectFormError>()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) errors += ProjectFormError.NAME_REQUIRED
        else if (trimmed.length > ProjectName.MAX_LENGTH) errors += ProjectFormError.NAME_TOO_LONG

        val parsedEmoji = try
        {
            emoji?.let { Emoji(it) }
        } catch (_: InvalidSubcategoryEmojiException)
        {
            errors += ProjectFormError.EMOJI_INVALID
            null
        }

        // Blank means "no target"; anything else must be a positive amount.
        val parsedTarget = if (targetText.isBlank()) null else parseAmountToCents(targetText)
        if (targetText.isNotBlank() && parsedTarget == null) errors += ProjectFormError.TARGET_INVALID

        if (errors.isNotEmpty()) return ProjectSubmission.Invalid(errors)

        val projectName = ProjectName(trimmed)
        val target = parsedTarget?.let { Money(it) }
        val threshold = AlertThreshold(thresholdPercent)
        return if (editingId != null)
        {
            ProjectSubmission.Update(UpdateProjectCommand(editingId, projectName, parsedEmoji, target, threshold))
        } else
        {
            ProjectSubmission.Create(CreateProjectCommand(projectName, parsedEmoji, target, threshold))
        }
    }
}

/**
 * The "new project" dialog opened from the transaction form: the form being filled and what is wrong
 * with the last attempt. The same form as the projects screen's, so the same rules and messages.
 */
data class NewProjectDraft(
    val form: ProjectFormState = ProjectFormState.creating(),
    val errors: Set<ProjectFormError> = emptySet(),
)
