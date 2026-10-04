package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.port.input.CreateRecurringTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand
import com.kyovo.cents.domain.port.input.UpdateTransactionCommand

sealed interface FormSubmission
{
    data class Record(val command: RecordTransactionCommand) : FormSubmission
    data class Transfer(val command: RecordTransferCommand) : FormSubmission
    /** A rule that records the transaction on its start date and then again at its pace (no transaction is recorded here). */
    data class Repeat(val command: CreateRecurringTransactionCommand) : FormSubmission
    data class Update(val command: UpdateTransactionCommand) : FormSubmission
    data class Invalid(val errors: Set<FormError>) : FormSubmission
}