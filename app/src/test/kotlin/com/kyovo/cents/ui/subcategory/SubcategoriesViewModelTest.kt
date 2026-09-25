package com.kyovo.cents.ui.subcategory

import org.junit.jupiter.api.extension.ExtendWith
import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.DuplicateSubcategoryNameException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.port.input.CreateSubcategoryCommand
import com.kyovo.cents.domain.port.input.CreateSubcategoryUseCase
import com.kyovo.cents.domain.port.input.DeleteSubcategoryUseCase
import com.kyovo.cents.domain.port.input.UpdateSubcategoryCommand
import com.kyovo.cents.domain.port.input.UpdateSubcategoryUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

private const val CART = "🛒"
private val EXPENSE = RecordableTransactionCategory.EXPENSE
private val INCOME = RecordableTransactionCategory.INCOME

/** Records what it is asked to create; can be told to refuse the name. */
private class RecordingCreate : CreateSubcategoryUseCase
{
    val commands = mutableListOf<CreateSubcategoryCommand>()
    var failWith: RuntimeException? = null

    override fun create(command: CreateSubcategoryCommand): Subcategory
    {
        failWith?.let { throw it }
        commands += command
        return Subcategory(SubcategoryId(Uuid.random()), command.kind, command.name, command.emoji)
    }
}

/** Records what it is asked to update; can be told to fail. */
private class RecordingUpdate : UpdateSubcategoryUseCase
{
    val commands = mutableListOf<UpdateSubcategoryCommand>()
    var failWith: RuntimeException? = null

    override fun update(command: UpdateSubcategoryCommand): Subcategory
    {
        failWith?.let { throw it }
        commands += command
        return Subcategory(command.id, EXPENSE, command.name, command.emoji)
    }
}

private class RecordingDelete : DeleteSubcategoryUseCase
{
    val deleted = mutableListOf<SubcategoryId>()

    override suspend fun delete(id: SubcategoryId)
    {
        deleted += id
    }
}

private fun aRow(
    name: String = "Alimentation",
    count: Int = 12,
    kind: RecordableTransactionCategory = EXPENSE
) =
    SubcategoryRow(
        Subcategory(
            SubcategoryId(Uuid.random()),
            kind,
            SubcategoryName(name),
            SubcategoryEmoji(CART)
        ),
        count,
    )

@ExtendWith(MainDispatcherExtension::class)
class SubcategoriesViewModelTest
{
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val revision = DataRevision()
    private val viewModel = SubcategoriesViewModel(create, update, delete, revision)

    private val state get() = viewModel.uiState.value
    private val form get() = state.form
    private val revisionNow get() = revision.value.value

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(state).isEqualTo(SubcategoriesUiState())
    }

    @Test
    fun `opening a creation starts an empty form of the section's kind`()
    {
        // WHEN
        viewModel.openCreate(INCOME)

        // THEN
        assertThat(form).isEqualTo(SubcategoryFormState.creating(INCOME))
        assertThat(state.errors).isEmpty()
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `opening an edit shows the subcategory's name and emoji`()
    {
        // GIVEN
        val row = aRow()

        // WHEN
        viewModel.openForEdit(row)

        // THEN
        assertThat(form).isEqualTo(SubcategoryFormState.editing(row.subcategory))
    }

    @Test
    fun `changing the form keeps it open and clears what the last attempt reported`()
    {
        // GIVEN a refused blank name
        viewModel.openCreate(EXPENSE)
        viewModel.submit()
        assertThat(state.errors).isNotEmpty()

        // WHEN
        viewModel.update(form!!.withName("Loisirs"))

        // THEN
        assertThat(form!!.name).isEqualTo("Loisirs")
        assertThat(state.errors).isEmpty()
    }

    @Test
    fun `changing the form while closed does nothing`()
    {
        // WHEN
        viewModel.update(SubcategoryFormState.creating(EXPENSE).withName("Loisirs"))

        // THEN
        assertThat(form).isNull()
    }

    // ------------------------------------------------------------------ creating

    @Test
    fun `a valid creation is saved, the lists are told to refresh and the sheet closes`()
    {
        // GIVEN
        viewModel.openCreate(INCOME)
        viewModel.update(form!!.withName(" Primes ").withEmoji(CART))
        val revisionBefore = revisionNow

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(create.commands).containsExactly(
            CreateSubcategoryCommand(INCOME, SubcategoryName("Primes"), SubcategoryEmoji(CART)),
        )
        assertThat(revisionNow).isEqualTo(revisionBefore + 1)
        assertThat(state).isEqualTo(SubcategoriesUiState())
    }

    @Test
    fun `a blank name is reported, nothing is saved and the sheet stays open`()
    {
        // GIVEN
        viewModel.openCreate(EXPENSE)
        val revisionBefore = revisionNow

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(SubcategoryFormError.NAME_REQUIRED)
        assertThat(form).isNotNull()
        assertThat(create.commands).isEmpty()
        assertThat(revisionNow).isEqualTo(revisionBefore)
    }

    @Test
    fun `a name already used is reported and the form is kept as typed`()
    {
        // GIVEN
        viewModel.openCreate(EXPENSE)
        viewModel.update(form!!.withName("Alimentation").withEmoji(CART))
        create.failWith = DuplicateSubcategoryNameException()
        val revisionBefore = revisionNow

        // WHEN
        viewModel.submit()

        // THEN nothing is lost: the user only has to change the name
        assertThat(state.errors).containsExactly(SubcategoryFormError.NAME_TAKEN)
        assertThat(form!!.name).isEqualTo("Alimentation")
        assertThat(form!!.emoji).isEqualTo(CART)
        assertThat(revisionNow).isEqualTo(revisionBefore)
    }

    @Test
    fun `submitting while closed does nothing`()
    {
        // WHEN
        viewModel.submit()

        // THEN
        assertThat(create.commands).isEmpty()
        assertThat(update.commands).isEmpty()
    }

    // ------------------------------------------------------------------ editing

    @Test
    fun `a valid edit is saved on that subcategory, the lists are told to refresh and the sheet closes`()
    {
        // GIVEN
        val row = aRow()
        viewModel.openForEdit(row)
        viewModel.update(form!!.withName("Courses"))
        val revisionBefore = revisionNow

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(update.commands).containsExactly(
            UpdateSubcategoryCommand(
                row.subcategory.id,
                SubcategoryName("Courses"),
                SubcategoryEmoji(CART)
            ),
        )
        assertThat(create.commands).isEmpty()
        assertThat(revisionNow).isEqualTo(revisionBefore + 1)
        assertThat(state).isEqualTo(SubcategoriesUiState())
    }

    @Test
    fun `removing the emoji of an edit saves it without one`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        viewModel.update(form!!.withEmoji(null))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(update.commands.single().emoji).isNull()
    }

    @Test
    fun `renaming to a name another subcategory has is reported, and the edit stays open`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        viewModel.update(form!!.withName("Transport"))
        update.failWith = DuplicateSubcategoryNameException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(SubcategoryFormError.NAME_TAKEN)
        assertThat(form!!.name).isEqualTo("Transport")
    }

    @Test
    fun `a subcategory that has gone since the edit began is reported, not a crash`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        update.failWith = SubcategoryNotFoundException()
        val revisionBefore = revisionNow

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(state.errors).containsExactly(SubcategoryFormError.SUBCATEGORY_GONE)
        assertThat(form).isNotNull()
        assertThat(revisionNow).isEqualTo(revisionBefore)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `asking to delete names the subcategory and says how many transactions lose it`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(name = "Alimentation", count = 12))

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isEqualTo(SubcategoryToDelete("Alimentation", 12))
        assertThat(form).isNotNull()
        assertThat(delete.deleted).isEmpty()
    }

    @Test
    fun `a subcategory nothing uses is asked about with a count of zero`()
    {
        // GIVEN
        viewModel.openForEdit(aRow(count = 0))

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete!!.transactionCount).isZero()
    }

    @Test
    fun `the question is about the stored subcategory, not what was typed in the form since`()
    {
        // GIVEN the name was changed but not saved
        viewModel.openForEdit(aRow(name = "Alimentation"))
        viewModel.update(form!!.withName("Autre chose"))

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete!!.name).isEqualTo("Alimentation")
    }

    @Test
    fun `there is nothing to delete in a new subcategory`()
    {
        // GIVEN
        viewModel.openCreate(EXPENSE)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `there is nothing to delete while closed`()
    {
        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
    }

    @Test
    fun `backing out of the question keeps the edit open and deletes nothing`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        viewModel.askToDelete()

        // WHEN
        viewModel.dismissDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
        assertThat(form).isNotNull()
        assertThat(delete.deleted).isEmpty()
    }

    @Test
    fun `confirming deletes that subcategory, refreshes the lists and closes everything`()
    {
        // GIVEN
        val row = aRow()
        viewModel.openForEdit(row)
        viewModel.askToDelete()
        val revisionBefore = revisionNow

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(delete.deleted).containsExactly(row.subcategory.id)
        assertThat(revisionNow).isEqualTo(revisionBefore + 1)
        assertThat(state).isEqualTo(SubcategoriesUiState())
    }

    // A deletion needs the question to have been asked: a stray call must not erase anything.
    @Test
    fun `confirming without having been asked deletes nothing`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        val revisionBefore = revisionNow

        // WHEN
        viewModel.confirmDelete()

        // THEN
        assertThat(delete.deleted).isEmpty()
        assertThat(revisionNow).isEqualTo(revisionBefore)
        assertThat(form).isNotNull()
    }

    @Test
    fun `closing drops the form, the errors and the question`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        viewModel.askToDelete()

        // WHEN
        viewModel.close()

        // THEN
        assertThat(state).isEqualTo(SubcategoriesUiState())
    }

    @Test
    fun `after closing, the question can't be asked again about the old row`()
    {
        // GIVEN
        viewModel.openForEdit(aRow())
        viewModel.close()
        viewModel.openCreate(EXPENSE)

        // WHEN
        viewModel.askToDelete()

        // THEN
        assertThat(state.confirmingDelete).isNull()
    }
}
