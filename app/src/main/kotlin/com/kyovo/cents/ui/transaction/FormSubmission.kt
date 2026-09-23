package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.port.input.RecordTransactionCommand
import com.kyovo.cents.domain.port.input.RecordTransferCommand

sealed interface FormSubmission
{
    data class Record(val command: RecordTransactionCommand) : FormSubmission
    data class Transfer(val command: RecordTransferCommand) : FormSubmission
    data class Invalid(val errors: Set<FormError>) : FormSubmission
}