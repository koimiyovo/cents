package com.kyovo.cents.domain.exception

class InvalidRecurringExpenseIntervalException :
    IllegalArgumentException("Recurring expense interval must be at least 1")
