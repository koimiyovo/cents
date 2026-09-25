package com.kyovo.cents.domain.exception

class InvalidRestoredTransactionException :
    IllegalStateException("Stored transaction does not match its category")
