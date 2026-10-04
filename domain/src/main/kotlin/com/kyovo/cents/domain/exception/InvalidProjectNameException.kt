package com.kyovo.cents.domain.exception

class InvalidProjectNameException :
    IllegalArgumentException("Project name must not be blank or too long")