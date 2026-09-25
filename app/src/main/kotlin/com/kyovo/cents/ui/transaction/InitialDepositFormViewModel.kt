package com.kyovo.cents.ui.transaction

import androidx.lifecycle.ViewModel
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.InvalidInitialDepositAmountException
import com.kyovo.cents.domain.exception.NotAnInitialDepositException
import com.kyovo.cents.domain.exception.TransactionNotFoundException
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.UpdateInitialDepositUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Why a valid amount still couldn't be saved. */
enum class InitialDepositFailure
{
    /** The deposit is gone, or is no longer an opening deposit. */
    DEPOSIT_UNAVAILABLE,
}

/** [form] is null while the sheet is closed, so "is the sheet open" and its content can't disagree. */
data class InitialDepositUiState(
    val form: InitialDepositFormState? = null,
    val showErrors: Boolean = false,
    val failure: InitialDepositFailure? = null,
)

/**
 * Holds the opening-deposit form across configuration changes, like [TransactionFormViewModel] does
 * for the transaction form (and for the same reason: an Activity-scoped ViewModel outlives rotation).
 */
class InitialDepositFormViewModel(
    private val updateInitialDeposit: UpdateInitialDepositUseCase,
    private val dataRevision: DataRevision,
) : ViewModel()
{
    private val _uiState = MutableStateFlow(InitialDepositUiState())
    val uiState: StateFlow<InitialDepositUiState> = _uiState.asStateFlow()

    /** Opens the form pre-filled with [transaction]'s amount. Anything but an opening deposit is ignored. */
    fun openForEdit(transaction: Transaction)
    {
        if (!canEditInitialDeposit(transaction)) return
        _uiState.value = InitialDepositUiState(form = InitialDepositFormState.editing(transaction))
    }

    fun update(form: InitialDepositFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, failure = null) }
    }

    fun close()
    {
        _uiState.value = InitialDepositUiState()
    }

    /** Saves the amount. On success the sheet closes; otherwise the state says what to show. */
    fun submit()
    {
        val form = _uiState.value.form ?: return
        when (val submission = form.submit())
        {
            InitialDepositSubmission.Invalid       ->
            {
                _uiState.update { it.copy(showErrors = true) }
                return
            }

            is InitialDepositSubmission.Update     ->
            {
                try
                {
                    updateInitialDeposit.update(submission.id, submission.amount)
                } catch (_: InvalidInitialDepositAmountException)
                {
                    // The form never lets a zero through, but the domain says no to one: show the amount error.
                    _uiState.update { it.copy(showErrors = true) }
                    return
                } catch (_: TransactionNotFoundException)
                {
                    _uiState.update { it.copy(failure = InitialDepositFailure.DEPOSIT_UNAVAILABLE) }
                    return
                } catch (_: NotAnInitialDepositException)
                {
                    _uiState.update { it.copy(failure = InitialDepositFailure.DEPOSIT_UNAVAILABLE) }
                    return
                }
            }
        }

        // Bump only after the write went through, then close: the lists re-read as the sheet leaves.
        dataRevision.bump()
        close()
    }
}
