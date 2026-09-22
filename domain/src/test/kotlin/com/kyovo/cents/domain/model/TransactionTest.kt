package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.uuid.Uuid

class TransactionTest
{
    private val id = TransactionId(Uuid.parse("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(Uuid.parse("11111111-1111-1111-1111-111111111111"))
    private val amount = Money(1_000)
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    @Test
    fun `an opening deposit is always of category INITIAL_DEPOSIT and has no subcategory`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.INITIAL_DEPOSIT)
        assertThat(transaction.subcategory).isNull()
    }

    @Test
    fun `an opening deposit has no description by default`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.description).isNull()
    }

    @Test
    fun `an opening deposit does not have a description`()
    {
        // WHEN
        val transaction =
            Transaction.openingDeposit(
                id,
                accountId,
                amount,
                date
            )

        // THEN
        assertThat(transaction.description).isNull()
    }

    @Test
    fun `a recorded expense is of category EXPENSE`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            RecordableTransactionCategory.EXPENSE,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.EXPENSE)
    }

    @Test
    fun `a recorded income is of category INCOME`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            RecordableTransactionCategory.INCOME,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.INCOME)
    }

    @Test
    fun `a recorded transaction can have a description`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            RecordableTransactionCategory.EXPENSE,
            ExpenseSubcategory.GROCERIES,
            TransactionDescription.of("Courses de la semaine"),
            date
        )

        // THEN
        assertThat(transaction.description?.value).isEqualTo("Courses de la semaine")
    }

    @Test
    fun `a recorded expense accepts an expense subcategory`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id = id,
            accountId = accountId,
            amount = amount,
            category = RecordableTransactionCategory.EXPENSE,
            subcategory = ExpenseSubcategory.FUEL,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.subcategory).isEqualTo(ExpenseSubcategory.FUEL)
    }

    @Test
    fun `a recorded income accepts an income subcategory`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id = id,
            accountId = accountId,
            amount = amount,
            category = RecordableTransactionCategory.INCOME,
            subcategory = IncomeSubcategory.SALARY,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.subcategory).isEqualTo(IncomeSubcategory.SALARY)
    }

    @Test
    fun `refuses an income subcategory on an expense transaction`()
    {
        // WHEN / THEN
        assertThatThrownBy {
            Transaction.recorded(
                id = id,
                accountId = accountId,
                amount = amount,
                category = RecordableTransactionCategory.EXPENSE,
                subcategory = IncomeSubcategory.SALARY,
                description = null,
                date = date
            )
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }

    @Test
    fun `refuses an expense subcategory on an income transaction`()
    {
        // WHEN / THEN
        assertThatThrownBy {
            Transaction.recorded(
                id = id,
                accountId = accountId,
                amount = amount,
                category = RecordableTransactionCategory.INCOME,
                subcategory = ExpenseSubcategory.GROCERIES,
                description = null,
                date = date
            )
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }
}
