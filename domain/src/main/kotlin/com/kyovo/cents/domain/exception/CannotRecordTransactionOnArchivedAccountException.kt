package com.kyovo.cents.domain.exception

class CannotRecordTransactionOnArchivedAccountException :
    IllegalArgumentException("An archived account must not have new transaction")