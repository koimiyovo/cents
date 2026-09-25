package com.kyovo.cents.domain.exception

class NotAnInitialDepositException :
    IllegalArgumentException("Transaction must be an initial deposit")