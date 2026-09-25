package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

class ListSubcategoriesService(private val subcategoryRepository: SubcategoryRepository) :
    ListSubcategoriesUseCase
{
    // Ascending by name, the way a French reader expects: case is ignored and an accented letter
    // sorts with its plain one ("Éducation" between "Divers" and "Loisirs"), which a plain string
    // comparison gets wrong. PRIMARY strength compares letters only; equal names keep stored order.
    private val byName: Comparator<Subcategory> = Collator.getInstance(Locale.FRENCH)
        .apply { strength = Collator.PRIMARY }
        .let { collator -> compareBy(collator) { it.name.value } }

    override fun observe(kind: RecordableTransactionCategory?): Flow<List<Subcategory>>
    {
        return subcategoryRepository.observeAll()
            .map { subcategories -> subcategories.filter { kind == null || it.kind == kind }.sortedWith(byName) }
            // A screen showing one kind has nothing to redraw when the other one changes.
            .distinctUntilChanged()
    }
}
