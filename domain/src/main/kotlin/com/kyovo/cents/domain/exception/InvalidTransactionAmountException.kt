package com.kyovo.cents.domain.exception

class InvalidTransactionAmountException :
    IllegalArgumentException("Transaction amount must be greater than 0")