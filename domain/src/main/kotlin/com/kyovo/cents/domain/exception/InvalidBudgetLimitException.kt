package com.kyovo.cents.domain.exception

class InvalidBudgetLimitException : IllegalArgumentException("Budget limit must be greater than 0")