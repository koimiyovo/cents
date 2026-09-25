package com.kyovo.cents.domain.exception

class CannotUpdateInitialDepositException :
    IllegalArgumentException("Initial deposit must not be updated")