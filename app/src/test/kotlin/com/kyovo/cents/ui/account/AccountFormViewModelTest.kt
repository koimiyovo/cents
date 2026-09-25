package com.kyovo.cents.ui.account

import org.junit.jupiter.api.extension.ExtendWith
import com.kyovo.cents.MainDispatcherExtension
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import com.kyovo.cents.domain.port.input.UpdateAccountCommand
import com.kyovo.cents.domain.port.input.UpdateAccountUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

/** Records what it is asked to open; can be told to refuse instead. */
private class FakeOpenAccount : OpenAccountUseCase
{
    val commands = mutableListOf<OpenAccountCommand>()
    var failWith: RuntimeException? = null

    override suspend fun open(command: OpenAccountCommand): Account
    {
        failWith?.let { throw it }
        commands += command
        return command.toAccount(AccountId(UUID.randomUUID()), Instant.parse("2026-09-23T12:00:00Z"))
    }
}

/** Records what it is asked to update; can be told to refuse instead. */
private class FakeUpdateAccount : UpdateAccountUseCase
{
    val commands = mutableListOf<UpdateAccountCommand>()
    var failWith: RuntimeException? = null

    override suspend fun update(command: UpdateAccountCommand): Account
    {
        failWith?.let { throw it }
        commands += command
        return command.toAccount(anExistingAccount(command.id))
    }
}

private fun anExistingAccount(id: AccountId = AccountId(UUID.randomUUID())) = Account(
    id = id,
    name = AccountName("Livret A"),
    type = AccountType.SAVINGS,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    description = AccountDescription.of("Épargne de précaution"),
)

@ExtendWith(MainDispatcherExtension::class)
class AccountFormViewModelTest
{
    private val openAccount = FakeOpenAccount()
    private val updateAccount = FakeUpdateAccount()
    private val viewModel = AccountFormViewModel(openAccount, updateAccount)

    private val form get() = viewModel.uiState.value.form

    private fun openAndFill(name: String = "Livret A")
    {
        viewModel.open()
        viewModel.update(
            form!!.copy(
                name = name,
                type = AccountType.SAVINGS,
                initialAmountText = "100"
            )
        )
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
    fun `a valid form opens the account and the sheet closes`()
    {
        // GIVEN
        openAndFill()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(openAccount.commands).hasSize(1)
        assertThat(openAccount.commands.single().type).isEqualTo(AccountType.SAVINGS)
        assertThat(form).isNull()
    }

    @Test
    fun `an invalid form opens nothing, stays open and starts showing its errors`()
    {
        // GIVEN
        viewModel.open()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(openAccount.commands).isEmpty()
        assertThat(form).isNotNull()
        assertThat(viewModel.uiState.value.showErrors).isTrue()
    }

    @Test
    fun `a name already in use keeps the sheet open and says why`()
    {
        // GIVEN
        openAndFill()
        openAccount.failWith = DuplicateAccountNameException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(AccountSubmitFailure.DUPLICATE_NAME)
        assertThat(form).isNotNull()
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

    @Test
    fun `opening for edit pre-fills the form with the account's values`()
    {
        // GIVEN
        val account = anExistingAccount()

        // WHEN
        viewModel.openForEdit(account)

        // THEN
        assertThat(form).isEqualTo(AccountFormState.editing(account))
        assertThat(form!!.isEditing).isTrue()
        assertThat(viewModel.uiState.value.showErrors).isFalse()
    }

    @Test
    fun `a valid edit updates the account instead of opening one and closes`()
    {
        // GIVEN
        val account = anExistingAccount()
        viewModel.openForEdit(account)
        viewModel.update(form!!.copy(name = "Livret B", type = AccountType.CHECKING))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(openAccount.commands).isEmpty()
        assertThat(updateAccount.commands).hasSize(1)
        assertThat(updateAccount.commands.single().id).isEqualTo(account.id)
        assertThat(updateAccount.commands.single().name).isEqualTo(AccountName("Livret B"))
        assertThat(updateAccount.commands.single().type).isEqualTo(AccountType.CHECKING)
        assertThat(form).isNull()
    }

    @Test
    fun `an edit with a blank name updates nothing, stays open and starts showing its errors`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingAccount())
        viewModel.update(form!!.copy(name = "  "))

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(updateAccount.commands).isEmpty()
        assertThat(form).isNotNull()
        assertThat(viewModel.uiState.value.showErrors).isTrue()
    }

    @Test
    fun `renaming to a name already in use keeps the sheet open and says why`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingAccount())
        viewModel.update(form!!.copy(name = "Compte courant"))
        updateAccount.failWith = DuplicateAccountNameException()

        // WHEN
        viewModel.submit()

        // THEN
        assertThat(viewModel.uiState.value.failure).isEqualTo(AccountSubmitFailure.DUPLICATE_NAME)
        assertThat(form).isNotNull()
    }

    @Test
    fun `opening a new account after an edit starts from an empty form`()
    {
        // GIVEN
        viewModel.openForEdit(anExistingAccount())
        viewModel.close()

        // WHEN
        viewModel.open()

        // THEN
        assertThat(form).isEqualTo(AccountFormState())
        assertThat(form!!.isEditing).isFalse()
    }
}
