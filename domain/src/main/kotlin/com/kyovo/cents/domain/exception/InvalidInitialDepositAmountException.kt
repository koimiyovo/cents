package com.kyovo.cents.domain.exception

class InvalidInitialDepositAmountException :
    IllegalArgumentException("Initial deposit must not be 0")
