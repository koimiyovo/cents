package com.kyovo.cents.domain.exception

class DuplicateProjectNameException :
    IllegalArgumentException("Project name must be unique")