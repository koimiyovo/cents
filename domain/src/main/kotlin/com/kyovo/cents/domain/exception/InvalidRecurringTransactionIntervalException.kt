package com.kyovo.cents.domain.exception

class InvalidRecurringTransactionIntervalException :
    IllegalArgumentException("Recurring transaction interval must be at least 1")
