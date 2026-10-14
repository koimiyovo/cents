package com.kyovo.cents.domain.port.input

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.TransactionTitle
import java.time.Instant

data class RecordTransferCommand(
    val fromAccountId: AccountId,
    val toAccountId: AccountId,
    val amount: Money,
    val title: TransactionTitle,
    val date: Instant,
    /**
     * What the destination account receives, in its own currency, for a transfer between two currencies: the user
     * types it when they know what their bank gave, which is not the ECB's rate. Null lets the exchange rates
     * kept on the device decide. Within one currency it can only be null or equal to [amount].
     */
    val destinationAmount: Money? = null
)