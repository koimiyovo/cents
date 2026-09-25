package com.kyovo.cents.domain.exception

class CannotDeleteAccountWithTransactionsException :
    IllegalArgumentException("Account with transactions must not be deleted")