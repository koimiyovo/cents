package com.kyovo.cents.domain.exception

class TransferToSameAccountException :
    IllegalArgumentException("Transfer accounts must be different")