package com.kyovo.cents.domain.exception

class InvalidAlertThresholdException :
    IllegalArgumentException("Alert threshold must be a whole percentage from 1 to 100")
