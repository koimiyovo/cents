package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.SubcategoryName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Subcategories are listed by name, in ascending order — whatever the order they were created or
 * stored in, so a renamed or newly created one lands where it belongs. The order ignores case and
 * accents' weight ("Éducation" sits between "Divers" and "Loisirs", not after "Transport").
 */
class ListSubcategoriesServiceTest
{
    private val repository = InMemorySubcategoryRepository()
    private val service = ListSubcategoriesService(repository)

    private fun aNamed(suffix: Int, name: String, kind: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE) =
        aSubcategory(
            id = aSubcategoryId("11111111-1111-1111-1111-11111111111$suffix"),
            kind = kind,
            name = SubcategoryName(name),
        )

    @Test
    fun `lists nothing when there is no subcategory`()
    {
        assertThat(service.list()).isEmpty()
    }

    @Test
    fun `lists every subcategory ordered by name, whatever the stored order`()
    {
        // GIVEN saved out of order
        val transport = aNamed(1, "Transport")
        val groceries = aNamed(2, "Alimentation", RecordableTransactionCategory.EXPENSE)
        val salary = aNamed(3, "Salaire", RecordableTransactionCategory.INCOME)
        repository.save(transport)
        repository.save(salary)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.list()).containsExactly(groceries, salary, transport)
    }

    @Test
    fun `lists only the subcategories of the given kind, ordered by name`()
    {
        // GIVEN
        val transport = aNamed(1, "Transport")
        val groceries = aNamed(2, "Alimentation")
        val salary = aNamed(3, "Salaire", RecordableTransactionCategory.INCOME)
        repository.save(transport)
        repository.save(salary)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.list(RecordableTransactionCategory.EXPENSE)).containsExactly(groceries, transport)
        assertThat(service.list(RecordableTransactionCategory.INCOME)).containsExactly(salary)
    }

    @Test
    fun `the order ignores case`()
    {
        // GIVEN
        val bills = aNamed(1, "factures")
        val leisure = aNamed(2, "Loisirs")
        val groceries = aNamed(3, "ALIMENTATION")
        repository.save(leisure)
        repository.save(bills)
        repository.save(groceries)

        // WHEN / THEN
        assertThat(service.list()).containsExactly(groceries, bills, leisure)
    }

    @Test
    fun `an accented letter sorts with its plain letter`()
    {
        // GIVEN
        val education = aNamed(1, "Éducation")
        val leisure = aNamed(2, "Loisirs")
        val misc = aNamed(3, "Divers")
        repository.save(leisure)
        repository.save(education)
        repository.save(misc)

        // WHEN / THEN
        assertThat(service.list()).containsExactly(misc, education, leisure)
    }

    @Test
    fun `a renamed subcategory moves to where its new name belongs`()
    {
        // GIVEN
        val groceries = aNamed(1, "Alimentation")
        val transport = aNamed(2, "Transport")
        repository.save(groceries)
        repository.save(transport)

        // WHEN it is renamed after the other one
        repository.save(groceries.copy(name = SubcategoryName("Zoo")))

        // THEN
        assertThat(service.list().map { it.name.value }).containsExactly("Transport", "Zoo")
    }

    // The same name under both kinds is legitimate ("Autre"); neither goes first by rule, so the
    // stored order decides — and stays, listing after listing.
    @Test
    fun `equal names keep the stored order`()
    {
        // GIVEN
        val incomeOther = aNamed(1, "Autre", RecordableTransactionCategory.INCOME)
        val expenseOther = aNamed(2, "Autre", RecordableTransactionCategory.EXPENSE)
        repository.save(incomeOther)
        repository.save(expenseOther)

        // WHEN / THEN
        assertThat(service.list()).containsExactly(incomeOther, expenseOther)
    }

    @Test
    fun `names that only differ by case or accents count as equal, and keep the stored order`()
    {
        // GIVEN
        val lower = aNamed(1, "éducation", RecordableTransactionCategory.INCOME)
        val plain = aNamed(2, "Education", RecordableTransactionCategory.EXPENSE)
        val other = aNamed(3, "Divers")
        repository.save(lower)
        repository.save(plain)
        repository.save(other)

        // WHEN / THEN
        assertThat(service.list()).containsExactly(other, lower, plain)
    }
}
