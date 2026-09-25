package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.domain.exception.InvalidSubcategoryEmojiException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.UpdateSubcategoryCommand
import com.kyovo.cents.ui.common.limitNameInput

/** Why a subcategory can't be saved. The first three are found by the form, the others by the use cases. */
enum class SubcategoryFormError
{
    NAME_REQUIRED,
    NAME_TOO_LONG,
    EMOJI_INVALID,

    /** A subcategory of the same kind already has that name (case, spaces and accents ignored). */
    NAME_TAKEN,

    /** Editing: the subcategory is gone since the form was opened. */
    SUBCATEGORY_GONE,
}

sealed interface SubcategorySubmission
{
    data class Create(val command: CreateSubcategoryCommand) : SubcategorySubmission
    data class Update(val command: UpdateSubcategoryCommand) : SubcategorySubmission
    data class Invalid(val errors: Set<SubcategoryFormError>) : SubcategorySubmission
}

/**
 * The form to create or edit a subcategory, as raw text: what is typed is parsed in [submit], like
 * the other forms. Only the name and the emoji can be edited — the kind is chosen when the
 * subcategory is created (the section it is created from) and never changes, so an edit doesn't
 * carry it.
 */
data class SubcategoryFormState(
    val kind: RecordableTransactionCategory,
    val name: String = "",
    val emoji: String? = null,
    /** Set when an existing subcategory is being edited instead of a new one created. */
    val editingId: SubcategoryId? = null,
)
{
    val isEditing: Boolean get() = editingId != null

    companion object
    {
        fun creating(kind: RecordableTransactionCategory): SubcategoryFormState = SubcategoryFormState(kind)

        fun editing(subcategory: Subcategory): SubcategoryFormState = SubcategoryFormState(
            kind = subcategory.kind,
            name = subcategory.name.value,
            emoji = subcategory.emoji?.value,
            editingId = subcategory.id,
        )
    }

    /** The name after an edit of the field: cut at the domain's limit, like every name field. */
    fun withName(text: String): SubcategoryFormState = copy(name = limitNameInput(text, SubcategoryName.MAX_LENGTH))

    /** The emoji picked; null removes it. */
    fun withEmoji(emoji: String?): SubcategoryFormState = copy(emoji = emoji)

    fun submit(): SubcategorySubmission
    {
        val errors = mutableSetOf<SubcategoryFormError>()

        val trimmed = name.trim()
        if (trimmed.isEmpty()) errors += SubcategoryFormError.NAME_REQUIRED
        else if (trimmed.length > SubcategoryName.MAX_LENGTH) errors += SubcategoryFormError.NAME_TOO_LONG

        val parsedEmoji = try
        {
            emoji?.let { SubcategoryEmoji(it) }
        } catch (_: InvalidSubcategoryEmojiException)
        {
            errors += SubcategoryFormError.EMOJI_INVALID
            null
        }

        if (errors.isNotEmpty()) return SubcategorySubmission.Invalid(errors)

        val subcategoryName = SubcategoryName(trimmed)
        return if (editingId != null)
        {
            SubcategorySubmission.Update(UpdateSubcategoryCommand(editingId, subcategoryName, parsedEmoji))
        } else
        {
            SubcategorySubmission.Create(CreateSubcategoryCommand(kind, subcategoryName, parsedEmoji))
        }
    }
}
