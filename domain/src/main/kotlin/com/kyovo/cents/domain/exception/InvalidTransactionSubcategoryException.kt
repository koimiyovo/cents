package com.kyovo.cents.domain.exception

class InvalidTransactionSubcategoryException :
    IllegalStateException("Transaction subcategory not matched")