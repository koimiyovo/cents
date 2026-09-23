package com.kyovo.cents.ui.account

import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

/** Records what it is asked to open; can be told to refuse instead. */
private class FakeOpenAccount : OpenAccountUseCase
{
    val commands = mutableListOf<OpenAccountCommand>()
    var failWith: RuntimeException? = null

    override fun open(command: OpenAccountCommand): Account
    {
        failWith?.let { throw it }
        commands += command
        return command.toAccount(AccountId(Uuid.random()), Instant.parse("2026-09-23T12:00:00Z"))
    }
}

class AccountFormViewModelTest
{
    private val openAccount = FakeOpenAccount()
    private val revision = DataRevision()
    private val viewModel = AccountFormViewModel(openAccount, revision)

    private val form get() = viewModel.uiState.value.form

    private fun openAndFill(name: String = "Livret A")
    {
        viewModel.open()
        viewModel.update(form!!.copy(name = name, type = AccountType.SAVINGS, initialAmountText = "100"))
    }

    @Test
    fun `is closed until a form is opened`()
    {
        assertThat(form).isNull()
    }

    @Test
    fun `opening starts an empty form`()
    {
        // WHEN
        viewModel.open()

        // THEN
        assertThat(form).isEqualTo(AccountFormState())
        assertThat(viewModel.uiState.value.showErrors).isFalse()
    }

    @Test
    fun `a valid form opens the account, tells the lists to refresh, and the sheet closes`()
    {
        // GIVEN
        openAndFill()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(openAccount.commands).hasSize(1)
        assertThat(openAccount.commands.single().type).isEqualTo(AccountType.SAVINGS)
        assertThat(revision.value.value).isEqualTo(revisionBefore + 1)
        assertThat(form).isNull()
    }

    @Test
    fun `an invalid form opens nothing, stays open and starts showing its errors`()
    {
        // GIVEN
        viewModel.open()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(openAccount.commands).isEmpty()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
        assertThat(form).isNotNull()
        assertThat(viewModel.uiState.value.showErrors).isTrue()
    }

    @Test
    fun `a name already in use keeps the sheet open and says why`()
    {
        // GIVEN
        openAndFill()
        openAccount.failWith = DuplicateAccountNameException()
        val revisionBefore = revision.value.value

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(AccountSubmitFailure.DUPLICATE_NAME)
        assertThat(form).isNotNull()
        assertThat(revision.value.value).isEqualTo(revisionBefore)
    }

    @Test
    fun `editing the form clears a previous failure`()
    {
        // GIVEN
        openAndFill()
        openAccount.failWith = DuplicateAccountNameException()
        viewModel.submit()

        // WHEN
        viewModel.update(form!!.copy(name = "Livret B"))

        // THEN
        assertThat(viewModel.uiState.value.failure).isNull()
        assertThat(form!!.name).isEqualTo("Livret B")
    }

    @Test
    fun `editing while closed does nothing`()
    {
        // WHEN
        viewModel.update(AccountFormState(name = "Ghost"))

        // THEN
        assertThat(form).isNull()
    }

    @Test
    fun `closing throws the form away, so the next one starts clean`()
    {
        // GIVEN
        openAndFill()

        // WHEN
        viewModel.close()
        viewModel.open()

        // THEN
        assertThat(form).isEqualTo(AccountFormState())
    }
}
