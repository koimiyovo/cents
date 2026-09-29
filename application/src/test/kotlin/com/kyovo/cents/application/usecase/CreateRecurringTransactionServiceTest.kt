package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedRecurringTransactionIdGenerator
import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryRecurringTransactionRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.aCreateRecurringTransactionCommand
import com.kyovo.cents.application.fakes.aRecurringTransactionId
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
class CreateRecurringTransactionServiceTest
{
    private val accountRepository = InMemoryAccountRepository()
    private val subcategoryRepository = InMemorySubcategoryRepository()
    private val recurringTransactionRepository = InMemoryRecurringTransactionRepository()
    private val id = aRecurringTransactionId()
    private val service = CreateRecurringTransactionService(
        recurringTransactionRepository, accountRepository, subcategoryRepository, FixedRecurringTransactionIdGenerator(id)
    )

    @Test
    fun `creates the rule when its account exists and it has no subcategory`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        accountRepository.save(anAccount(id = accountId))

        // WHEN
        val created = service.create(aCreateRecurringTransactionCommand(accountId = accountId, subcategoryId = null))

        // THEN
        assertThat(created.id).isEqualTo(id)
        assertThat(recurringTransactionRepository.saved).containsExactly(created)
    }

    @Test
    fun `refuses an unknown account, nothing saved`() = runTest()
    {
        // WHEN / THEN
        assertThatThrownBySuspending {
            service.create(aCreateRecurringTransactionCommand(accountId = anAccountId()))
        }.isInstanceOf(AccountNotFoundException::class.java)
        assertThat(recurringTransactionRepository.saved).isEmpty()
    }

    @Test
    fun `refuses an unknown subcategory, nothing saved`() = runTest()
    {
        // GIVEN
        val accountId = anAccountId()
        accountRepository.save(anAccount(id = accountId))

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.create(aCreateRecurringTransactionCommand(accountId = accountId, subcategoryId = aSubcategoryId()))
        }.isInstanceOf(SubcategoryNotFoundException::class.java)
        assertThat(recurringTransactionRepository.saved).isEmpty()
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
            service.create(aCreateRecurringTransactionCommand(accountId = accountId, subcategoryId = incomeSubcategoryId))
        }.isInstanceOf(InvalidTransactionSubcategoryException::class.java)
        assertThat(recurringTransactionRepository.saved).isEmpty()
    }
}
