package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidTransactionAmountException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.UUID

class TransactionTest
{
    private val id = TransactionId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val accountId = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val amount = Money(1_000)
    private val title = TransactionTitle("Courses de la semaine")
    private val date = Instant.parse("2026-09-22T10:00:00Z")

    // A transaction stores the *id* of its subcategory, but is built from the subcategory itself:
    // that is what lets it check, at construction, that the subcategory fits its category.
    private val groceries = Subcategory(
        SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
        RecordableTransactionCategory.EXPENSE,
        SubcategoryName("Alimentation"),
        null
    )
    private val salary = Subcategory(
        SubcategoryId(UUID.fromString("66666666-6666-6666-6666-666666666666")),
        RecordableTransactionCategory.INCOME,
        SubcategoryName("Salaire"),
        null
    )

    @Test
    fun `an opening deposit is always of category INITIAL_DEPOSIT and has no subcategory`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.INITIAL_DEPOSIT)
        assertThat(transaction.subcategoryId).isNull()
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
    fun `an opening deposit has a default title`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.title.value).isEqualTo("Dépôt initial")
    }

    @Test
    fun `a recorded expense is of category EXPENSE`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            title,
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
            title,
            RecordableTransactionCategory.INCOME,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.INCOME)
    }

    @Test
    fun `a recorded transaction has the given title`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            title,
            RecordableTransactionCategory.EXPENSE,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.title).isEqualTo(title)
    }

    @Test
    fun `a recorded transaction can have a description`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            title,
            RecordableTransactionCategory.EXPENSE,
            groceries,
            TransactionDescription.of("Courses de la semaine"),
            date
        )

        // THEN
        assertThat(transaction.description?.value).isEqualTo("Courses de la semaine")
    }

    @Test
    fun `a recorded transaction has no subcategory unless one is given`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id = id,
            accountId = accountId,
            amount = amount,
            title = title,
            category = RecordableTransactionCategory.EXPENSE,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.subcategoryId).isNull()
    }

    @Test
    fun `a recorded expense accepts an expense subcategory and keeps its id`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id = id,
            accountId = accountId,
            amount = amount,
            title = title,
            category = RecordableTransactionCategory.EXPENSE,
            subcategory = groceries,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.subcategoryId).isEqualTo(groceries.id)
    }

    @Test
    fun `a recorded income accepts an income subcategory and keeps its id`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id = id,
            accountId = accountId,
            amount = amount,
            title = title,
            category = RecordableTransactionCategory.INCOME,
            subcategory = salary,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.subcategoryId).isEqualTo(salary.id)
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
                title = title,
                category = RecordableTransactionCategory.EXPENSE,
                subcategory = salary,
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
                title = title,
                category = RecordableTransactionCategory.INCOME,
                subcategory = groceries,
                description = null,
                date = date
            )
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
    }

    // Money accepts 0 (a balance can be 0), but recording an income or an expense of nothing is
    // meaningless: it would only add a row that changes no balance. The form already refuses it;
    // the domain must too, since it is the one that guarantees its own rules.
    @ParameterizedTest
    @EnumSource(RecordableTransactionCategory::class)
    fun `refuses to record a transaction of zero`(category: RecordableTransactionCategory)
    {
        // WHEN / THEN
        assertThatThrownBy {
            Transaction.recorded(
                id = id,
                accountId = accountId,
                amount = Money(0),
                title = title,
                category = category,
                subcategory = null,
                description = null,
                date = date
            )
        }.isInstanceOf(InvalidTransactionAmountException::class.java)
    }

    @Test
    fun `an opening deposit contributes its full amount to the balance`()
    {
        // WHEN
        val transaction = Transaction.openingDeposit(id, accountId, amount, date)

        // THEN
        assertThat(transaction.signedAmount).isEqualTo(1_000L)
    }

    @Test
    fun `a recorded income contributes its full amount to the balance`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            title,
            RecordableTransactionCategory.INCOME,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.signedAmount).isEqualTo(1_000L)
    }

    @Test
    fun `a recorded expense contributes its negated amount to the balance`()
    {
        // WHEN
        val transaction = Transaction.recorded(
            id,
            accountId,
            amount,
            title,
            RecordableTransactionCategory.EXPENSE,
            subcategory = null,
            description = null,
            date = date
        )

        // THEN
        assertThat(transaction.signedAmount).isEqualTo(-1_000L)
    }

    @Test
    fun `a transfer-out leg is of category TRANSFER_OUT and has no subcategory`()
    {
        // WHEN
        val transaction = Transaction.transferOut(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.TRANSFER_OUT)
        assertThat(transaction.subcategoryId).isNull()
    }

    @Test
    fun `a transfer-in leg is of category TRANSFER_IN and has no subcategory`()
    {
        // WHEN
        val transaction = Transaction.transferIn(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.category).isEqualTo(TransactionCategory.TRANSFER_IN)
        assertThat(transaction.subcategoryId).isNull()
    }

    @Test
    fun `a transfer-out leg has no description`()
    {
        // WHEN
        val transaction = Transaction.transferOut(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.description).isNull()
    }

    @Test
    fun `a transfer-in leg has no description`()
    {
        // WHEN
        val transaction = Transaction.transferIn(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.description).isNull()
    }

    @Test
    fun `a transfer leg has the given title`()
    {
        // WHEN
        val outTransaction = Transaction.transferOut(id, accountId, amount, title, date)
        val inTransaction = Transaction.transferIn(id, accountId, amount, title, date)

        // THEN
        assertThat(outTransaction.title).isEqualTo(title)
        assertThat(inTransaction.title).isEqualTo(title)
    }

    // Same rule as `recorded`: a leg of nothing moves no money. Either factory refuses it, so a transfer
    // of zero cannot be built one leg at a time either.
    @Test
    fun `refuses a transfer-out leg of zero`()
    {
        // WHEN / THEN
        assertThatThrownBy { Transaction.transferOut(id, accountId, Money(0), title, date) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
    }

    @Test
    fun `refuses a transfer-in leg of zero`()
    {
        // WHEN / THEN
        assertThatThrownBy { Transaction.transferIn(id, accountId, Money(0), title, date) }
            .isInstanceOf(InvalidTransactionAmountException::class.java)
    }

    @Test
    fun `a transfer-out leg contributes its negated amount to the balance`()
    {
        // WHEN
        val transaction = Transaction.transferOut(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.signedAmount).isEqualTo(-1_000L)
    }

    @Test
    fun `a transfer-in leg contributes its full amount to the balance`()
    {
        // WHEN
        val transaction = Transaction.transferIn(id, accountId, amount, title, date)

        // THEN
        assertThat(transaction.signedAmount).isEqualTo(1_000L)
    }
}
