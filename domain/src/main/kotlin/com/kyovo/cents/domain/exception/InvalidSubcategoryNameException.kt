package com.kyovo.cents.domain.exception

class InvalidSubcategoryNameException :
    IllegalArgumentException("Subcategory name must not be blank")