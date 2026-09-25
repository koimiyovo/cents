package com.kyovo.cents.domain.port.output

import com.kyovo.cents.domain.model.SubcategoryId

interface SubcategoryIdGenerator
{
    fun generate(): SubcategoryId
}