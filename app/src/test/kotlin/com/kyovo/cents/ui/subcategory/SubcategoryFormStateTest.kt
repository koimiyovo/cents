package com.kyovo.cents.ui.subcategory

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.UpdateSubcategoryCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.uuid.Uuid

private const val CART = "🛒"
private val EXPENSE = RecordableTransactionCategory.EXPENSE
private val INCOME = RecordableTransactionCategory.INCOME

private fun aSubcategory(emoji: String? = CART, kind: RecordableTransactionCategory = EXPENSE) = Subcategory(
    SubcategoryId(Uuid.parse("aaaaaaaa-0000-0000-0000-000000000001")),
    kind,
    SubcategoryName("Alimentation"),
    emoji?.let { SubcategoryEmoji(it) },
)

class SubcategoryFormCreatingTest
{
    @Test
    fun `a new form is empty and knows the kind of the section it comes from`()
    {
        // WHEN
        val form = SubcategoryFormState.creating(INCOME)

        // THEN
        assertThat(form.kind).isEqualTo(INCOME)
        assertThat(form.name).isEmpty()
        assertThat(form.emoji).isNull()
        assertThat(form.isEditing).isFalse()
    }

    @Test
    fun `a name and an emoji make a creation, with the name trimmed`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withName("  Loisirs ").withEmoji(CART)

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            SubcategorySubmission.Create(
                CreateSubcategoryCommand(EXPENSE, SubcategoryName("Loisirs"), SubcategoryEmoji(CART)),
            ),
        )
    }

    @Test
    fun `the emoji is optional`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withName("Loisirs")

        // WHEN
        val submission = form.submit() as SubcategorySubmission.Create

        // THEN
        assertThat(submission.command.emoji).isNull()
    }

    @Test
    fun `an income form creates an income subcategory`()
    {
        // WHEN
        val submission = SubcategoryFormState.creating(INCOME).withName("Primes").submit() as SubcategorySubmission.Create

        // THEN
        assertThat(submission.command.kind).isEqualTo(INCOME)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   ", "\t"])
    fun `a blank name is refused`(name: String)
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withName(name)

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(SubcategorySubmission.Invalid(setOf(SubcategoryFormError.NAME_REQUIRED)))
    }

    @Test
    fun `an emoji the domain refuses is refused`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withName("Loisirs").withEmoji("  ")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(SubcategorySubmission.Invalid(setOf(SubcategoryFormError.EMOJI_INVALID)))
    }

    @Test
    fun `every problem is reported at once`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withEmoji("  ")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(
            SubcategorySubmission.Invalid(setOf(SubcategoryFormError.NAME_REQUIRED, SubcategoryFormError.EMOJI_INVALID)),
        )
    }
}

class SubcategoryFormNameTest
{
    @Test
    fun `typing stops at the domain's limit`()
    {
        // WHEN
        val form = SubcategoryFormState.creating(EXPENSE).withName("x".repeat(500))

        // THEN
        assertThat(form.name).hasSize(SubcategoryName.MAX_LENGTH)
    }

    @Test
    fun `a name of exactly the limit is accepted`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).withName("x".repeat(SubcategoryName.MAX_LENGTH))

        // WHEN / THEN
        assertThat(form.submit()).isInstanceOf(SubcategorySubmission.Create::class.java)
    }

    // withName can't produce it, but a copy can: the domain's "no" is an answer, not a crash.
    @Test
    fun `a name over the limit that got in anyway is refused as too long, not as missing`()
    {
        // GIVEN
        val form = SubcategoryFormState.creating(EXPENSE).copy(name = "x".repeat(SubcategoryName.MAX_LENGTH + 1))

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(SubcategorySubmission.Invalid(setOf(SubcategoryFormError.NAME_TOO_LONG)))
    }

    @Test
    fun `the limit is about the name, not the spaces around it`()
    {
        // GIVEN 40 characters and a space
        val form = SubcategoryFormState.creating(EXPENSE)
            .copy(name = "x".repeat(SubcategoryName.MAX_LENGTH) + " ")

        // WHEN / THEN
        assertThat(form.submit()).isInstanceOf(SubcategorySubmission.Create::class.java)
    }
}

class SubcategoryFormEditingTest
{
    @Test
    fun `editing pre-fills the name and the emoji, and remembers which subcategory it is`()
    {
        // GIVEN
        val subcategory = aSubcategory()

        // WHEN
        val form = SubcategoryFormState.editing(subcategory)

        // THEN
        assertThat(form.name).isEqualTo("Alimentation")
        assertThat(form.emoji).isEqualTo(CART)
        assertThat(form.editingId).isEqualTo(subcategory.id)
        assertThat(form.isEditing).isTrue()
    }

    @Test
    fun `a subcategory without an emoji is edited with none`()
    {
        assertThat(SubcategoryFormState.editing(aSubcategory(emoji = null)).emoji).isNull()
    }

    @Test
    fun `the kind is kept for display, of an income as of an expense`()
    {
        assertThat(SubcategoryFormState.editing(aSubcategory(kind = INCOME)).kind).isEqualTo(INCOME)
        assertThat(SubcategoryFormState.editing(aSubcategory(kind = EXPENSE)).kind).isEqualTo(EXPENSE)
    }

    @Test
    fun `saving an edit updates that subcategory, without a kind to change`()
    {
        // GIVEN
        val subcategory = aSubcategory()
        val form = SubcategoryFormState.editing(subcategory).withName("Courses")

        // WHEN
        val submission = form.submit()

        // THEN
        assertThat(submission).isEqualTo(
            SubcategorySubmission.Update(
                UpdateSubcategoryCommand(subcategory.id, SubcategoryName("Courses"), SubcategoryEmoji(CART)),
            ),
        )
    }

    @Test
    fun `removing the emoji saves an update with none`()
    {
        // GIVEN
        val form = SubcategoryFormState.editing(aSubcategory()).withEmoji(null)

        // WHEN
        val submission = form.submit() as SubcategorySubmission.Update

        // THEN
        assertThat(submission.command.emoji).isNull()
    }

    @Test
    fun `an untouched edit saves the name and the emoji it was opened with`()
    {
        // GIVEN
        val subcategory = aSubcategory()

        // WHEN
        val submission = SubcategoryFormState.editing(subcategory).submit() as SubcategorySubmission.Update

        // THEN
        assertThat(submission.command).isEqualTo(
            UpdateSubcategoryCommand(subcategory.id, subcategory.name, subcategory.emoji),
        )
    }

    @Test
    fun `clearing the name of an edit is refused`()
    {
        // GIVEN
        val form = SubcategoryFormState.editing(aSubcategory()).withName("")

        // WHEN / THEN
        assertThat(form.submit()).isEqualTo(SubcategorySubmission.Invalid(setOf(SubcategoryFormError.NAME_REQUIRED)))
    }
}
