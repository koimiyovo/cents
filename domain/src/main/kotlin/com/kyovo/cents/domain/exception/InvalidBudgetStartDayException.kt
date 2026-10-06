package com.kyovo.cents.domain.exception

class InvalidBudgetStartDayException :
    IllegalArgumentException("Budget start day must be between 1 and 28")