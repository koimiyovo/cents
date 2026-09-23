package com.kyovo.cents.ui.account

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.port.input.OpenAccountCommand
import com.kyovo.cents.ui.common.parseAmountToCents
import java.util.Currency

/**
 * Every account is opened in euros for now: amounts are formatted as euros throughout the UI, so
 * offering another currency here would display its balance with the wrong symbol. A currency picker
 * comes with the exchange-rate work.
 */
val DEFAULT_ACCOUNT_CURRENCY = AccountCurrency(Currency.getInstance("EUR"))

enum class AccountFormError
{
    NAME_REQUIRED,
    INITIAL_AMOUNT_INVALID,
}

sealed interface AccountFormSubmission
{
    data class Open(val command: OpenAccountCommand) : AccountFormSubmission
    data class Invalid(val errors: Set<AccountFormError>) : AccountFormSubmission
}

/**
 * What the user has typed in the "new account" form, as raw text (see the transaction form for the
 * same approach): validation happens in [submit]. The name's uniqueness isn't checked here — that
 * is the use case's rule, and it reports it.
 */
data class AccountFormState(
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val initialAmountText: String = "",
    val description: String = "",
)
{
    fun submit(): AccountFormSubmission
    {
        val errors = mutableSetOf<AccountFormError>()

        if (name.isBlank()) errors += AccountFormError.NAME_REQUIRED

        // An account may start empty: a blank field means 0, and an explicit 0 is fine too.
        val initialCents =
            if (initialAmountText.isBlank()) 0L else parseAmountToCents(initialAmountText, allowZero = true)
        if (initialCents == null) errors += AccountFormError.INITIAL_AMOUNT_INVALID

        if (errors.isNotEmpty() || initialCents == null) return AccountFormSubmission.Invalid(errors)

        return AccountFormSubmission.Open(
            OpenAccountCommand(
                name = AccountName(name),
                type = type,
                currency = DEFAULT_ACCOUNT_CURRENCY,
                initialAmount = Money(initialCents),
                description = AccountDescription.of(description),
            ),
        )
    }
}
