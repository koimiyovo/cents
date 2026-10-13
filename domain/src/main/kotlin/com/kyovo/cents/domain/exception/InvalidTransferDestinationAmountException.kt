package com.kyovo.cents.domain.exception

/**
 * Within one currency, what a transfer credits is what it debits: money cannot change value, so a different amount
 * received would make some appear or disappear. Only a transfer between two currencies has an amount received of its own.
 */
class InvalidTransferDestinationAmountException :
    IllegalArgumentException("Between two accounts of the same currency, the amount received is the amount sent")
