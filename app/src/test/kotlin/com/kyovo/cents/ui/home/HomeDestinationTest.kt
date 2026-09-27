package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The screens below the tabs: the settings, and under them the subcategory management and the budget
 * limits. The Back button goes up one level at a time, and on the tabs it is left to the system (which leaves the app).
 */
class HomeDestinationTest
{
    @Test
    fun `back from the subcategories goes to the settings`()
    {
        assertThat(HomeDestination.Subcategories.back()).isEqualTo(HomeDestination.Settings)
    }

    @Test
    fun `back from the budget limits goes to the settings`()
    {
        assertThat(HomeDestination.Budgets.back()).isEqualTo(HomeDestination.Settings)
    }

    @Test
    fun `back from the recurring expenses goes to the settings`()
    {
        assertThat(HomeDestination.RecurringExpenses.back()).isEqualTo(HomeDestination.Settings)
    }

    @Test
    fun `back from the settings goes to the tabs`()
    {
        assertThat(HomeDestination.Settings.back()).isEqualTo(HomeDestination.Tabs)
    }

    @Test
    fun `there is nothing above the tabs`()
    {
        assertThat(HomeDestination.Tabs.back()).isNull()
    }

    @Test
    fun `going back from the deepest screen reaches the tabs in two steps, never loops`()
    {
        // WHEN the user keeps pressing Back
        val path = generateSequence(HomeDestination.Subcategories) { it.back() }.toList()

        // THEN
        assertThat(path).containsExactly(HomeDestination.Subcategories, HomeDestination.Settings, HomeDestination.Tabs)
    }

    @Test
    fun `the budget limits are as deep as the subcategories, and just as easy to leave`()
    {
        val path = generateSequence(HomeDestination.Budgets) { it.back() }.toList()

        assertThat(path).containsExactly(HomeDestination.Budgets, HomeDestination.Settings, HomeDestination.Tabs)
    }

    @Test
    fun `every destination but the tabs has somewhere to go back to`()
    {
        val withBack = HomeDestination.entries.filter { it.back() != null }

        assertThat(withBack).containsExactlyInAnyOrder(
            HomeDestination.Settings,
            HomeDestination.Subcategories,
            HomeDestination.Budgets,
            HomeDestination.RecurringExpenses,
        )
    }
}
