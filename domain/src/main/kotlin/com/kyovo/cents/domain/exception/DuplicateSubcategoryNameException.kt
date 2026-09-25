package com.kyovo.cents.domain.exception

class DuplicateSubcategoryNameException :
    IllegalArgumentException("Subcategory name must be unique")