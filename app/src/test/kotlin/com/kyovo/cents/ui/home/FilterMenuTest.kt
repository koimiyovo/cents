package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The three filters of the Transactions screen (period, account, subcategory) sit on one bar and share the
 * list that unfolds below it, so at most one is open: tapping a filter opens its list, tapping it again
 * folds it, tapping another switches to that one.
 */
class FilterMenuTest
{
    @Test
    fun `tapping a filter while none is open opens it`()
    {
        assertThat(toggledMenu(open = null, tapped = FilterMenu.ACCOUNT)).isEqualTo(FilterMenu.ACCOUNT)
    }

    @Test
    fun `tapping the open filter folds it`()
    {
        assertThat(toggledMenu(open = FilterMenu.PERIOD, tapped = FilterMenu.PERIOD)).isNull()
    }

    @Test
    fun `tapping another filter switches to it, without closing in between`()
    {
        assertThat(toggledMenu(open = FilterMenu.PERIOD, tapped = FilterMenu.SUBCATEGORY))
            .isEqualTo(FilterMenu.SUBCATEGORY)
    }
}
