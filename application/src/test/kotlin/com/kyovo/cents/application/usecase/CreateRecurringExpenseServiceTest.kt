package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedRecurringExpenseIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringExpenseRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aCreateRecurringExpenseCommand
import com.kyovo.cents.application.fakes.aRecurringExpenseId
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.AccountNotFoundException
import com.kyovo.cents.domain.exception.InvalidTransactionSubcategoryException
import com.kyovo.cents.domain.exception.SubcategoryNotFoundException
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateRecurringExpenseServiceTest
{
    private val accountRepository = InMemoryAccountRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val recurringExpenseRepository = InMemoryRecurringExpenseRepository()
    private val id = aRecurringExpenseId()
    private val service = CreateRecurringExpenseService(
        recurringExpenseRepository, accountRepository, subcategoryRepository, FixedRecurringExpenseIdGenerator(id)
    )

    @Test
    fun `creates the rule when its account exists and it has no subcategory`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        accountRepository.save(anAccount(id = accountId))

        // WHEN
        val created = service.create(aCreateRecurringExpenseCommand(accountId = accountId, subcategoryId = null))

        // THEN
        assertThat(created.id).isEqualTo(id)
        assertThat(recurringExpenseRepository.saved).containsExactly(created)
    }

    @Test
    fun `refuses an unknown account, nothing saved`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending {
            service.create(aCreateRecurringExpenseCommand(accountId = anAccountId()))
        }.isInstanceOf(AccountNotFoundException::class.java)
        assertThat(recurringExpenseRepository.saved).isEmpty()
    }

    @Test
    fun `refuses an unknown subcategory, nothing saved`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        accountRepository.save(anAccount(id = accountId))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.create(aCreateRecurringExpenseCommand(accountId = accountId, subcategoryId = aSubcategoryId()))
        }.isInstanceOf(SubcategoryNotFoundException::class.java)
        assertThat(recurringExpenseRepository.saved).isEmpty()
    }

    @Test
    fun `refuses an income subcategory, since a recurring expense is always an expense`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        accountRepository.save(anAccount(id = accountId))
        val incomeSubcategoryId = aSubcategoryId()
        subcategoryRepository.save(aSubcategory(id = incomeSubcategoryId, kind = RecordableTransactionCategory.INCOME))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.create(aCreateRecurringExpenseCommand(accountId = accountId, subcategoryId = incomeSubcategoryId))
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
        assertThat(recurringExpenseRepository.saved).isEmpty()
    }
}
