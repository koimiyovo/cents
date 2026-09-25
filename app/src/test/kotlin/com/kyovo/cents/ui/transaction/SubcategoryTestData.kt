package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import kotlin.uuid.Uuid

// Subcategories shared by the form tests: two of the expense kind, one of the income kind.

internal val GROCERIES_SUBCATEGORY = Subcategory(
    SubcategoryId(Uuid.parse("aaaaaaaa-0000-0000-0000-000000000001")),
    RecordableTransactionCategory.EXPENSE,
    SubcategoryName("Alimentation"),
    null,
)

internal val FUEL_SUBCATEGORY = Subcategory(
    SubcategoryId(Uuid.parse("aaaaaaaa-0000-0000-0000-000000000002")),
    RecordableTransactionCategory.EXPENSE,
    SubcategoryName("Transport"),
    null,
)

internal val SALARY_SUBCATEGORY = Subcategory(
    SubcategoryId(Uuid.parse("bbbbbbbb-0000-0000-0000-000000000001")),
    RecordableTransactionCategory.INCOME,
    SubcategoryName("Salaire"),
    null,
)

internal val ALL_TEST_SUBCATEGORIES = listOf(GROCERIES_SUBCATEGORY, FUEL_SUBCATEGORY, SALARY_SUBCATEGORY)

/** The test subcategory an id designates, the way the screen resolves a transaction's subcategory. */
internal fun subcategoryFor(id: SubcategoryId?): Subcategory? = ALL_TEST_SUBCATEGORIES.find { it.id == id }

/**
 * The real API asks for the transaction's subcategory explicitly (a default would let a caller
 * silently drop it); tests that don't care resolve it from the transaction like the screen does.
 */
internal fun TransactionFormState.Companion.editing(transaction: Transaction): TransactionFormState =
    editing(transaction, subcategoryFor(transaction.subcategoryId))

internal fun TransactionFormViewModel.openForEdit(transaction: Transaction) =
    openForEdit(transaction, subcategoryFor(transaction.subcategoryId))
