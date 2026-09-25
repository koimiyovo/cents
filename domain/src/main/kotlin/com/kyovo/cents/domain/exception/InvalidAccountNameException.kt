package com.kyovo.cents.domain.exception

class InvalidAccountNameException : IllegalArgumentException("Account name must not be blank or too long")
