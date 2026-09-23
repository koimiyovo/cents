package com.kyovo.cents.ui.transaction

import androidx.lifecycle.ViewModel
import com.kyovo.cents.data.DataRevision
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.CannotRecordTransactionOnArchivedAccountException
import com.kyovo.cents.domain.exception.TransferToSameAccountException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.port.input.RecordTransactionUseCase
import com.kyovo.cents.domain.port.input.RecordTransferUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Why a syntactically valid form still couldn't be saved (rules only the use cases can check). */
enum class SubmitFailure
{
    ACCOUNT_NOT_FOUND,
    ARCHIVED_ACCOUNT,
    SAME_ACCOUNT,
}

/** [form] is null while the sheet is closed, so "is the sheet open" and its content can't disagree. */
data class TransactionFormUiState(
    val form: TransactionFormState? = null,
    val showErrors: Boolean = false,
    val failure: SubmitFailure? = null,
)

/**
 * Holds the transaction form across configuration changes: the Activity (and its composables) is
 * destroyed and recreated on rotation, a ViewModel is not — so what the user typed, and whether the
 * sheet is open, survive it. The screen only renders [uiState] and reports events back; nothing here
 * touches Compose or Android, which keeps it testable as plain Kotlin.
 *
 * The use cases are synchronous because storage is in memory. Once they hit Room they become
 * `suspend` and [submit] will launch them in `viewModelScope`.
 */
class TransactionFormViewModel(
    private val recordTransaction: RecordTransactionUseCase,
    private val recordTransfer: RecordTransferUseCase,
    private val dataRevision: DataRevision,
    private val now: () -> Instant = { Instant.now() },
) : ViewModel()
{
    private val _uiState = MutableStateFlow(TransactionFormUiState())
    val uiState: StateFlow<TransactionFormUiState> = _uiState.asStateFlow()

    /** Opens a fresh form. Called when the user asks for a new transaction, never on recomposition. */
    fun open(accounts: List<Account>, preselectedAccountId: AccountId?)
    {
        // With a single active account there is nothing to choose: skip the step.
        val onlyAccountId = selectableAccounts(accounts).singleOrNull()?.id
        _uiState.value = TransactionFormUiState(
            form = TransactionFormState.initial(
                accounts = accounts,
                preselectedAccountId = preselectedAccountId ?: onlyAccountId,
                now = now(),
            ),
        )
    }

    fun update(form: TransactionFormState)
    {
        _uiState.update { if (it.form == null) it else it.copy(form = form, failure = null) }
    }

    fun close()
    {
        _uiState.value = TransactionFormUiState()
    }

    /** Saves the form. On success the sheet closes; otherwise the state says what to show. */
    fun submit()
    {
        val form = _uiState.value.form ?: return
        val submission = form.stampedAt(now()).submit()
        try
        {
            when (submission)
            {
                is FormSubmission.Invalid  ->
                {
                    _uiState.update { it.copy(showErrors = true) }
                    return
                }

                is FormSubmission.Record   -> recordTransaction.record(submission.command)
                is FormSubmission.Transfer -> recordTransfer.record(submission.command)
            }
        } catch (_: AccountNotFoundException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.ACCOUNT_NOT_FOUND) }
            return
        } catch (_: CannotRecordTransactionOnArchivedAccountException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.ARCHIVED_ACCOUNT) }
            return
        } catch (_: TransferToSameAccountException)
        {
            _uiState.update { it.copy(failure = SubmitFailure.SAME_ACCOUNT) }
            return
        }

        // Bump only after the write went through, then close: the lists re-read as the sheet leaves.
        dataRevision.bump()
        close()
    }
}
