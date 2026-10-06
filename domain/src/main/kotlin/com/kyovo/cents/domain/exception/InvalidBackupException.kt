package com.kyovo.cents.domain.exception

class InvalidBackupException(cause: Throwable? = null) :
    IllegalArgumentException("The backup file is not a valid backup", cause)
