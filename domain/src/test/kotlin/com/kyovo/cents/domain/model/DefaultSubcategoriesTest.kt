package com.kyovo.cents.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The subcategories the app is delivered with: common ones, so that a new user can categorise a first
 * transaction without creating anything. They are ordinary subcategories from then on — the user can
 * rename or delete them — and they must obey the rules any subcategory obeys.
 */
class DefaultSubcategoriesTest
{
    private val all = DefaultSubcategories.ALL
    private val expenses = all.filter { it.kind == RecordableTransactionCategory.EXPENSE }
    private val incomes = all.filter { it.kind == RecordableTransactionCategory.INCOME }

    @Test
    fun `there are common subcategories for both kinds`()
    {
        assertThat(expenses.size).isGreaterThanOrEqualTo(8)
        assertThat(incomes.size).isGreaterThanOrEqualTo(4)
    }

    @Test
    fun `the everyday ones are there`()
    {
        assertThat(expenses.map { it.name.value }).contains("Alimentation", "Logement", "Transport", "Santé", "Loisirs")
        assertThat(incomes.map { it.name.value }).contains("Salaire", "Remboursement")
    }

    @Test
    fun `every subcategory has an emoji`()
    {
        assertThat(all.filter { it.emoji == null }).isEmpty()
    }

    @Test
    fun `every subcategory has its own id`()
    {
        assertThat(all.map { it.id }).doesNotHaveDuplicates()
    }

    // The same rule as when the user creates one: within a kind, two names that read the same clash
    // (case, spaces and accents ignored). "Autre" under both kinds is fine.
    @Test
    fun `no two subcategories of a kind have names that read the same`()
    {
        for (kind in listOf(expenses, incomes))
        {
            for ((index, subcategory) in kind.withIndex())
            {
                assertThat(kind.drop(index + 1).any { it.name.matches(subcategory.name) })
                    .describedAs("a duplicate of ${subcategory.name.value}")
                    .isFalse()
            }
        }
    }

    @Test
    fun `an id comes from a fixed key, not from chance`()
    {
        // A future version of the app may need to point at one of these (to add a new common
        // subcategory next to them, say), so an id is derived from a key that never changes.
        val alimentation = expenses.single { it.name.value == "Alimentation" }

        assertThat(alimentation.id).isEqualTo(DefaultSubcategories.idOf("expense:groceries"))
        assertThat(DefaultSubcategories.idOf("expense:groceries")).isEqualTo(DefaultSubcategories.idOf("expense:groceries"))
        assertThat(DefaultSubcategories.idOf("expense:groceries")).isNotEqualTo(DefaultSubcategories.idOf("income:salary"))
    }
}
