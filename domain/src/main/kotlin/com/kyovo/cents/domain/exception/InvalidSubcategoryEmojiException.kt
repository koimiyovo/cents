package com.kyovo.cents.domain.exception

class InvalidSubcategoryEmojiException :
    IllegalArgumentException("Subcategory emoji must not be blank or too long")
