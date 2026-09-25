package com.kyovo.cents.domain.exception

class CannotDeleteInitialDepositException :
    IllegalArgumentException("Initial deposit must not be deleted")