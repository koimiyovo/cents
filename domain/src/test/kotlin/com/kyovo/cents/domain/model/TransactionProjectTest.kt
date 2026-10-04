package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidRestoredTransactionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.UUID

/**
 * A transaction can belong to a project, next to its subcategory (two independent axes). Only an income
 * or an expense can: a transfer or an opening deposit has none, structurally - like the subcategory.
 */
class TransactionProjectTest
{
    private val id = TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val projectId = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
    private val date = Instant.parse("2026-09-22T10:00:00Z")
    private val title = TransactionTitle("Billets d'avion")

    private fun recorded(category: RecordableTransactionCategory, projectId: ProjectId? = this.projectId) =
        Transaction.recorded(id, accountId, Money(80_000), title, category, null, null, date, projectId)

    @ParameterizedTest
    @EnumSource(RecordableTransactionCategory::class)
    fun `an income or an expense can be recorded for a project`(category: RecordableTransactionCategory)
    {
        assertThat(recorded(category).projectId).isEqualTo(projectId)
    }

    @Test
    fun `a transaction recorded without a project has none`()
    {
        val transaction = Transaction.recorded(
            id, accountId, Money(80_000), title, RecordableTransactionCategory.EXPENSE, null, null, date
        )

        assertThat(transaction.projectId).isNull()
    }

    @Test
    fun `the project is independent of the subcategory`()
    {
        val restaurants = Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
            RecordableTransactionCategory.EXPENSE, SubcategoryName("Restaurants"), null,
        )

        val transaction = Transaction.recorded(
            id, accountId, Money(4_500), title, RecordableTransactionCategory.EXPENSE, restaurants, null, date, projectId
        )

        assertThat(transaction.subcategoryId).isEqualTo(restaurants.id)
        assertThat(transaction.projectId).isEqualTo(projectId)
    }

    @Test
    fun `an opening deposit and the two legs of a transfer have no project`()
    {
        assertThat(Transaction.openingDeposit(id, accountId, Money(1_000), date).projectId).isNull()
        assertThat(Transaction.transferOut(id, accountId, Money(1_000), title, date).projectId).isNull()
        assertThat(Transaction.transferIn(id, accountId, Money(1_000), title, date).projectId).isNull()
    }

    @Test
    fun `without its project, a transaction is the same in every other way`()
    {
        val original = recorded(RecordableTransactionCategory.EXPENSE)

        val result = original.withoutProject()

        assertThat(result).isEqualTo(recorded(RecordableTransactionCategory.EXPENSE, projectId = null))
        assertThat(result.signedAmount).isEqualTo(original.signedAmount)
    }

    @Test
    fun `dropping the project leaves the receiver unchanged, and a transaction without one equal`()
    {
        val original = recorded(RecordableTransactionCategory.EXPENSE)
        original.withoutProject()

        assertThat(original.projectId).isEqualTo(projectId)
        val none = recorded(RecordableTransactionCategory.EXPENSE, projectId = null)
        assertThat(none.withoutProject()).isEqualTo(none)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["EXPENSE", "INCOME"])
    fun `restored gives back the project of an income or an expense`(category: TransactionCategory)
    {
        val restored = Transaction.restored(id, accountId, Money(80_000), title, category, null, null, date, projectId)

        assertThat(restored.projectId).isEqualTo(projectId)
    }

    @ParameterizedTest
    @EnumSource(value = TransactionCategory::class, names = ["INITIAL_DEPOSIT", "TRANSFER_OUT", "TRANSFER_IN"])
    fun `restored refuses a project on anything but an income or an expense`(category: TransactionCategory)
    {
        assertThatThrownBy {
            Transaction.restored(id, accountId, Money(80_000), title, category, null, null, date, projectId)
        }.isInstanceOf(InvalidRestoredTransactionException::class.java)
    }
}
