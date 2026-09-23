package com.kyovo.cents.ui.account

import androidx.lifecycle.ViewModel
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.DuplicateAccountNameException
import com.kyovo.cents.domain.port.input.OpenAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Why a valid-looking form still could not be saved (a rule only the use case can check). */
enum class AccountSubmitFailure
{
    DUPLICATE_NAME,
}

/** [form] is null while the sheet is closed. */
data class AccountFormUiState(
    val form: AccountFormState? = null,
    val showErrors: Boolean = false,
    val failure: AccountSubmitFailure? = null,
)

/**
 * Holds the "new account" form across configuration changes, the same way
 * [com.kyovo.cents.ui.transaction.TransactionFormViewModel] does for transactions.
 */
class AccountFormViewModel(
    private val openAccount: OpenAccountUseCase,
    private val dataRevision: DataRevision,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(AccountFormUiState())
    val uiState: StateFlow<AccountFormUiState> = _uiState.asStateFlow()

    /** Opens an empty form. Called when the user asks for a new account, never on recomposition. */
    fun open()
    {
        _uiState.value = AccountFormUiState(form = AccountFormState())
    }

    fun update(form: AccountFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, failure = null) }
    }

    fun close()
    {
        _uiState.value = AccountFormUiState()
    }

    /** Saves the form. On success the sheet closes; otherwise the state says what to show. */
    fun submit()
    {
        val form = _uiState.value.form ?: return
        when (val submission = form.submit())
        {
            is AccountFormSubmission.Invalid -> _uiState.update { it.copy(showErrors = true) }
            is AccountFormSubmission.Open    ->
            {
                try
                {
                    openAccount.open(submission.command)
                } catch (_: DuplicateAccountNameException)
                {
                    _uiState.update { it.copy(failure = AccountSubmitFailure.DUPLICATE_NAME) }
                    return
                }
                // Bump only after the write went through, then close: the lists re-read as the sheet leaves.
                dataRevision.bump()
                close()
            }
        }
    }
}
