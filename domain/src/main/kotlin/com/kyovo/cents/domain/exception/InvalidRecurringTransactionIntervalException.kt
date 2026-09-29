package com.kyovo.cents.domain.exception

class InvalidRecurringTransactionIntervalException :
    IllegalArgumentException("Recurring expense interval must be at least 1")
