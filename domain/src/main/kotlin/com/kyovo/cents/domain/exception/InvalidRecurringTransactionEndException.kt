package com.kyovo.cents.domain.exception

class InvalidRecurringTransactionEndException :
    IllegalArgumentException("Recurring transaction end month must not be before its start month")
