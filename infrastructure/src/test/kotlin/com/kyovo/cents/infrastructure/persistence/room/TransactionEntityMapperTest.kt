package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/** The transaction's row is its own type, of plain values: cents, nanoseconds, names and ids. */
class TransactionEntityMapperTest
{
    private val id = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val account = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val subcategory = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val date = Instant.parse("2026-09-22T10:00:00.123456789Z")

    private val groceries = Subcategory(SubcategoryId(subcategory), RecordableTransactionCategory.EXPENSE, SubcategoryName("Alimentation"), null)

    private val expense = Transaction.recorded(
        TransactionId(id), AccountId(account), Money(1_250), TransactionTitle("Courses"),
        RecordableTransactionCategory.EXPENSE, groceries, TransactionDescription.of("Marché"), date,
    )

    @Test
    fun `a transaction becomes a row of plain values`()
    {
        assertThat(expense.toEntity()).isEqualTo(
            TransactionEntity(
                id = id,
                accountId = account,
                amount = 1_250,
                title = "Courses",
                category = "EXPENSE",
                subcategoryId = subcategory,
                description = "Marché",
                date = date.toEpochNanos(),
                projectId = null,
            ),
        )
    }

    private val projectUuid = UUID.fromString("66666666-6666-6666-6666-666666666661")

    @Test
    fun `the project of a transaction goes to its row and comes back`()
    {
        // GIVEN an expense of a project, and an income (a refund) of the same project
        val inProject = Transaction.recorded(
            TransactionId(id), AccountId(account), Money(80_000), TransactionTitle("Billets"),
            RecordableTransactionCategory.EXPENSE, groceries, null, date, ProjectId(projectUuid),
        )
        val refund = Transaction.recorded(
            TransactionId(id), AccountId(account), Money(5_000), TransactionTitle("Remboursement"),
            RecordableTransactionCategory.INCOME, null, null, date, ProjectId(projectUuid),
        )

        // WHEN / THEN
        assertThat(inProject.toEntity().projectId).isEqualTo(projectUuid)
        assertThat(inProject.toEntity().toDomain()).isEqualTo(inProject)
        assertThat(refund.toEntity().projectId).isEqualTo(projectUuid)
        assertThat(refund.toEntity().toDomain()).isEqualTo(refund)
    }

    @Test
    fun `a row without a project becomes a transaction without a project`()
    {
        assertThat(expense.toEntity().toDomain().projectId).isNull()
    }

    @Test
    fun `a row that links a project to anything but an income or an expense is refused`()
    {
        // GIVEN a transfer row that carries a project
        val transfer = Transaction.transferOut(TransactionId(id), AccountId(account), Money(300), TransactionTitle("Retrait"), date).toEntity()

        // WHEN / THEN
        assertThatThrownBy { transfer.copy(projectId = projectUuid).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a transaction without a subcategory or a description has neither in its row`()
    {
        // GIVEN
        val bare = Transaction.recorded(TransactionId(id), AccountId(account), Money(1), TransactionTitle("x"), RecordableTransactionCategory.INCOME, null, null, date)

        // WHEN
        val entity = bare.toEntity()

        // THEN
        assertThat(entity.category).isEqualTo("INCOME")
        assertThat(entity.subcategoryId).isNull()
        assertThat(entity.description).isNull()
    }

    @Test
    fun `every kind of transaction comes back as it went`()
    {
        // GIVEN one of each kind
        val all = listOf(
            expense,
            Transaction.openingDeposit(TransactionId(id), AccountId(account), Money(5_000), date),
            Transaction.transferOut(TransactionId(id), AccountId(account), Money(300), TransactionTitle("Retrait"), date),
            Transaction.transferIn(TransactionId(id), AccountId(account), Money(300), TransactionTitle("Retrait"), date),
            Transaction.recorded(TransactionId(id), AccountId(account), Money(9), TransactionTitle("Prime"), RecordableTransactionCategory.INCOME, null, null, date),
        )

        // WHEN / THEN
        all.forEach { assertThat(it.toEntity().toDomain()).isEqualTo(it) }
    }

    @Test
    fun `a row with an unknown category is refused instead of being guessed`()
    {
        assertThatThrownBy { expense.toEntity().copy(category = "REFUND").toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("REFUND")
    }

    @Test
    fun `a row that no transaction could have produced is refused`()
    {
        // GIVEN an opening deposit row that carries a subcategory
        val deposit = Transaction.openingDeposit(TransactionId(id), AccountId(account), Money(5_000), date).toEntity()

        // WHEN / THEN
        assertThatThrownBy { deposit.copy(subcategoryId = subcategory).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
