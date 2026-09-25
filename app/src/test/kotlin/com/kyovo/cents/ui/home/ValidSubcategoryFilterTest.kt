package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.ui.transaction.FUEL_SUBCATEGORY
import com.kyovo.cents.ui.transaction.GROCERIES_SUBCATEGORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

/**
 * A screen keeps the subcategory it was filtering on, and one can be deleted from the management
 * screen in the meantime. The filter must then fall back to "all" instead of leaving an empty list
 * that nothing explains.
 */
class ValidSubcategoryFilterTest
{
    private val all = listOf(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY)

    @Test
    fun `no filter stays no filter`()
    {
        assertThat(validSubcategoryFilter(null, all)).isNull()
        assertThat(validSubcategoryFilter(null, emptyList())).isNull()
    }

    @Test
    fun `a filter on an existing subcategory is kept`()
    {
        assertThat(validSubcategoryFilter(FUEL_SUBCATEGORY.id, all)).isEqualTo(FUEL_SUBCATEGORY.id)
    }

    @Test
    fun `a filter on a subcategory that no longer exists falls back to all`()
    {
        // GIVEN the one being filtered on was deleted
        val remaining = listOf(GROCERIES_SUBCATEGORY)

        // WHEN / THEN
        assertThat(validSubcategoryFilter(FUEL_SUBCATEGORY.id, remaining)).isNull()
    }

    @Test
    fun `a filter falls back to all when there is no subcategory left at all`()
    {
        assertThat(validSubcategoryFilter(SubcategoryId(Uuid.random()), emptyList())).isNull()
    }
}
