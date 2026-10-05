package com.kyovo.cents.domain.exception

class DuplicateBudgetCycleStartException :
    IllegalArgumentException("Two declared starts open the same budget cycle")
