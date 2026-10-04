package com.kyovo.cents.domain.exception

class InvalidProjectTargetException :
    IllegalArgumentException("Project target must be greater than 0")