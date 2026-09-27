package com.kyovo.cents.domain.exception

class InvalidRecurringExpenseEndException :
    IllegalArgumentException("Recurring expense end month must not be before its start month")
