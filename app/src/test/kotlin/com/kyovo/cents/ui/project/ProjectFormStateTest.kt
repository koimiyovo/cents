package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.port.input.CreateProjectCommand
import com.kyovo.cents.domain.port.input.UpdateProjectCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

private const val PLANE = "✈️"

/**
 * The form to create or edit a project: a name, an optional emoji and an optional target, as raw text.
 * Like the other forms it is stricter than the domain where that helps (the target is typed like an
 * amount: at most two decimals, never zero) and reports every problem at once.
 */
class ProjectFormStateTest
{
    private val id = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666661"))
    private val japan = Project(id, ProjectName("Voyage au Japon"), Emoji(PLANE), Money(300_050))

    // ------------------------------------------------------------------ opening

    @Test
    fun `a new form is empty and is not an edit`()
    {
        val form = ProjectFormState.creating()

        assertThat(form).isEqualTo(ProjectFormState())
        assertThat(form.name).isEmpty()
        assertThat(form.emoji).isNull()
        assertThat(form.targetText).isEmpty()
        assertThat(form.isEditing).isFalse()
    }

    @Test
    fun `editing a project pre-fills its name, emoji and target`()
    {
        val form = ProjectFormState.editing(japan)

        assertThat(form.name).isEqualTo("Voyage au Japon")
        assertThat(form.emoji).isEqualTo(PLANE)
        assertThat(form.targetText).isEqualTo("3000,50")
        assertThat(form.editingId).isEqualTo(id)
        assertThat(form.isEditing).isTrue()
    }

    @Test
    fun `editing a project without an emoji or a target leaves them empty`()
    {
        val form = ProjectFormState.editing(japan.copy(emoji = null, target = null))

        assertThat(form.emoji).isNull()
        assertThat(form.targetText).isEmpty()
    }

    // ------------------------------------------------------------------ typing

    @Test
    fun `the name is cut at the domain limit while typing`()
    {
        val form = ProjectFormState.creating().withName("x".repeat(ProjectName.MAX_LENGTH + 10))

        assertThat(form.name).hasSize(ProjectName.MAX_LENGTH)
    }

    @Test
    fun `an emoji can be picked and taken away`()
    {
        val picked = ProjectFormState.creating().withEmoji(PLANE)

        assertThat(picked.emoji).isEqualTo(PLANE)
        assertThat(picked.withEmoji(null).emoji).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "3000", "3000,", "3000,5", "3000,50", "3000.50"])
    fun `the target field accepts what an amount field accepts`(text: String)
    {
        assertThat(ProjectFormState.creating().withTarget(text).targetText).isEqualTo(text)
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "12,345", "1,2,3", "-5", "12 €"])
    fun `an edit that would break the shape of the target is dropped`(text: String)
    {
        val form = ProjectFormState.creating().withTarget("120")

        assertThat(form.withTarget(text).targetText).isEqualTo("120")
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `a valid new project becomes a create command`()
    {
        val form = ProjectFormState.creating().withName("  Voyage au Japon  ").withEmoji(PLANE).withTarget("3000,50")

        assertThat(form.submit()).isEqualTo(
            ProjectSubmission.Create(CreateProjectCommand(ProjectName("Voyage au Japon"), Emoji(PLANE), Money(300_050)))
        )
    }

    @Test
    fun `a project needs only a name`()
    {
        val form = ProjectFormState.creating().withName("Travaux cuisine")

        assertThat(form.submit()).isEqualTo(
            ProjectSubmission.Create(CreateProjectCommand(ProjectName("Travaux cuisine"), null, null))
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   "])
    fun `a blank target means no target`(text: String)
    {
        val form = ProjectFormState.creating().withName("Travaux").copy(targetText = text)

        assertThat((form.submit() as ProjectSubmission.Create).command.target).isNull()
    }

    @Test
    fun `an edit becomes an update command that carries the whole new state`()
    {
        val form = ProjectFormState.editing(japan).withName("Japon 2027").withEmoji(null).withTarget("")

        assertThat(form.submit()).isEqualTo(
            ProjectSubmission.Update(UpdateProjectCommand(id, ProjectName("Japon 2027"), null, null))
        )
    }

    @Test
    fun `an edit that changes nothing sends the project as it was`()
    {
        assertThat(ProjectFormState.editing(japan).submit()).isEqualTo(
            ProjectSubmission.Update(UpdateProjectCommand(id, japan.name, japan.emoji, japan.target))
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `a blank name is refused`(name: String)
    {
        val form = ProjectFormState.creating().copy(name = name)

        assertThat(form.submit()).isEqualTo(ProjectSubmission.Invalid(setOf(ProjectFormError.NAME_REQUIRED)))
    }

    @Test
    fun `a name that is too long is refused, even if it did not come through the field`()
    {
        val form = ProjectFormState.creating().copy(name = "x".repeat(ProjectName.MAX_LENGTH + 1))

        assertThat(form.submit()).isEqualTo(ProjectSubmission.Invalid(setOf(ProjectFormError.NAME_TOO_LONG)))
    }

    @Test
    fun `a blank emoji is refused`()
    {
        val form = ProjectFormState.creating().withName("Voyage").withEmoji("  ")

        assertThat(form.submit()).isEqualTo(ProjectSubmission.Invalid(setOf(ProjectFormError.EMOJI_INVALID)))
    }

    // A target of zero would be "over" at the first cent: the form refuses it like the domain.
    @ParameterizedTest
    @ValueSource(strings = ["0", "0,00", "0.0", ","])
    fun `a target that is not a positive amount is refused`(text: String)
    {
        val form = ProjectFormState.creating().withName("Voyage").copy(targetText = text)

        assertThat(form.submit()).isEqualTo(ProjectSubmission.Invalid(setOf(ProjectFormError.TARGET_INVALID)))
    }

    @Test
    fun `every problem is reported at once`()
    {
        val form = ProjectFormState.creating().copy(name = " ", emoji = " ", targetText = "0")

        assertThat((form.submit() as ProjectSubmission.Invalid).errors).containsExactlyInAnyOrder(
            ProjectFormError.NAME_REQUIRED, ProjectFormError.EMOJI_INVALID, ProjectFormError.TARGET_INVALID,
        )
    }
}
