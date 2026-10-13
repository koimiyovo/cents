package com.kyovo.cents.domain.exception

/** Without exchange rates, money can only move between accounts held in the same currency. */
class TransferBetweenDifferentCurrenciesException :
    IllegalArgumentException("Transfer accounts must have the same currency")
