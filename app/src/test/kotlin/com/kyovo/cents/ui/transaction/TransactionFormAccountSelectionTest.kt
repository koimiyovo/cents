package com.kyovo.cents.ui.transaction

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

private val SOMETIME = Instant.parse("2026-09-23T12:00:00Z")

private val CHECKING = AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
private val SAVINGS = AccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
private val NEW_ONE = AccountId(UUID.fromString("33333333-3333-3333-3333-333333333333"))

private fun anAccount(id: AccountId, archived: Boolean = false) = Account(
    id = id,
    name = AccountName("Compte ${id.value}"),
    type = AccountType.CHECKING,
    currency = AccountCurrency(Currency.getInstance("EUR")),
    createdAt = SOMETIME,
    archivedAt = if (archived) SOMETIME else null,
)

private fun aTransfer(from: AccountId? = CHECKING, to: AccountId? = SAVINGS) = TransactionFormState(
    type = TransactionFormType.TRANSFER,
    accountId = from,
    toAccountId = to,
    amountText = "50",
    title = "Épargne",
    date = SOMETIME,
)

/**
 * Choosing an account for a field of the form, as the "+ Créer un compte" action of the accounts
 * dropdown does once the account exists: the source ("the account") or, for a transfer, the destination.
 */
class TransactionFormWithAccountSelectedTest
{
    @Test
    fun `chooses the source`()
    {
        // GIVEN
        val form = aTransfer(from = null, to = null)

        // WHEN
        val result = form.withAccountSelected(AccountField.SOURCE, NEW_ONE)

        // THEN
        assertThat(result.accountId).isEqualTo(NEW_ONE)
    }

    @Test
    fun `replaces the source that was chosen`()
    {
        // WHEN
        val result = aTransfer(from = CHECKING, to = null).withAccountSelected(AccountField.SOURCE, NEW_ONE)

        // THEN
        assertThat(result.accountId).isEqualTo(NEW_ONE)
    }

    // A transfer to the same account is meaningless: the destination goes when it becomes the source.
    @Test
    fun `a new source that is also the destination clears the destination`()
    {
        // WHEN
        val result = aTransfer(from = CHECKING, to = SAVINGS).withAccountSelected(AccountField.SOURCE, SAVINGS)

        // THEN
        assertThat(result.accountId).isEqualTo(SAVINGS)
        assertThat(result.toAccountId).isNull()
    }

    @Test
    fun `a new source different from the destination keeps the destination`()
    {
        // WHEN
        val result = aTransfer(from = CHECKING, to = SAVINGS).withAccountSelected(AccountField.SOURCE, NEW_ONE)

        // THEN
        assertThat(result.accountId).isEqualTo(NEW_ONE)
        assertThat(result.toAccountId).isEqualTo(SAVINGS)
    }

    @Test
    fun `chooses the destination and leaves the source alone`()
    {
        // WHEN
        val result = aTransfer(from = CHECKING, to = null).withAccountSelected(AccountField.DESTINATION, NEW_ONE)

        // THEN
        assertThat(result.toAccountId).isEqualTo(NEW_ONE)
        assertThat(result.accountId).isEqualTo(CHECKING)
    }

    @Test
    fun `replaces the destination that was chosen`()
    {
        // WHEN
        val result = aTransfer(from = CHECKING, to = SAVINGS).withAccountSelected(AccountField.DESTINATION, NEW_ONE)

        // THEN
        assertThat(result.toAccountId).isEqualTo(NEW_ONE)
    }

    @Test
    fun `keeps everything else that was typed`()
    {
        // GIVEN
        val form = aTransfer()

        // WHEN
        val source = form.withAccountSelected(AccountField.SOURCE, NEW_ONE)
        val destination = form.withAccountSelected(AccountField.DESTINATION, NEW_ONE)

        // THEN
        listOf(source, destination).forEach {
            assertThat(it.type).isEqualTo(TransactionFormType.TRANSFER)
            assertThat(it.amountText).isEqualTo("50")
            assertThat(it.title).isEqualTo("Épargne")
            assertThat(it.date).isEqualTo(SOMETIME)
        }
    }

    @Test
    fun `changes nothing about a form when the account chosen is the one it already has`()
    {
        // GIVEN
        val form = aTransfer(from = CHECKING, to = SAVINGS)

        // WHEN / THEN
        assertThat(form.withAccountSelected(AccountField.SOURCE, CHECKING)).isEqualTo(form)
        assertThat(form.withAccountSelected(AccountField.DESTINATION, SAVINGS)).isEqualTo(form)
    }
}

/**
 * When the accounts change while the form is open, the form chooses the account for the user only
 * when there is nothing left to decide: none chosen yet, and exactly one account that can be.
 */
class TransactionFormWithSoleAccountSelectedTest
{
    private val empty = TransactionFormState(type = TransactionFormType.EXPENSE, accountId = null, date = SOMETIME)

    @Test
    fun `chooses the only active account when none is chosen`()
    {
        // WHEN
        val result = empty.withSoleAccountSelected(listOf(anAccount(CHECKING)))

        // THEN
        assertThat(result.accountId).isEqualTo(CHECKING)
    }

    @Test
    fun `does not choose among several accounts`()
    {
        // WHEN
        val result = empty.withSoleAccountSelected(listOf(anAccount(CHECKING), anAccount(SAVINGS)))

        // THEN
        assertThat(result.accountId).isNull()
    }

    @Test
    fun `an archived account doesn't count, it takes no transaction`()
    {
        // WHEN one active account and one archived
        val result = empty.withSoleAccountSelected(listOf(anAccount(CHECKING), anAccount(SAVINGS, archived = true)))

        // THEN the active one is the only choice
        assertThat(result.accountId).isEqualTo(CHECKING)
    }

    @Test
    fun `chooses nothing when the only account is archived`()
    {
        assertThat(empty.withSoleAccountSelected(listOf(anAccount(CHECKING, archived = true))).accountId).isNull()
    }

    @Test
    fun `chooses nothing when there is no account at all`()
    {
        assertThat(empty.withSoleAccountSelected(emptyList()).accountId).isNull()
    }

    @Test
    fun `leaves an account that is already chosen`()
    {
        // GIVEN the user chose savings, and checking is now the only one listed
        val chosen = empty.copy(accountId = SAVINGS)

        // WHEN
        val result = chosen.withSoleAccountSelected(listOf(anAccount(CHECKING)))

        // THEN
        assertThat(result.accountId).isEqualTo(SAVINGS)
    }

    @Test
    fun `keeps what was typed`()
    {
        // GIVEN
        val typed = empty.copy(amountText = "12,50", title = "Courses")

        // WHEN
        val result = typed.withSoleAccountSelected(listOf(anAccount(CHECKING)))

        // THEN
        assertThat(result.amountText).isEqualTo("12,50")
        assertThat(result.title).isEqualTo("Courses")
    }
}
