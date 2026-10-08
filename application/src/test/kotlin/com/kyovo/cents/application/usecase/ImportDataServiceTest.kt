package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.RecordingBackupRestorer
import com.kyovo.cents.application.fakes.RecordingBackupSerializer
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.InvalidBackupException
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.model.BackupSummary
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.TransactionCategory
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Importing a backup replaces everything the user owns with what the file holds. The file is read and
 * checked completely first: nothing is replaced unless all of it can be trusted, because what replaces
 * the data is what the data becomes. (That the replacing itself is all-or-nothing is the storage's
 * contract, tested where the storage is.)
 */
class ImportDataServiceTest
{
    private val serializer = RecordingBackupSerializer()
    private val restorer = RecordingBackupRestorer()
    private val service = ImportDataService(serializer, restorer)

    private val account = anAccount()

    private val consistent = BackupSnapshot(
        accounts = listOf(account),
        subcategories = listOf(aSubcategory()),
        transactions = listOf(aTransaction(accountId = account.id)),
        budgets = emptyList(),
        budgetCalendar = BudgetCalendar(),
        recurringTransactions = emptyList(),
        projects = emptyList()
    )

    // A transaction on an account that is not in the file: each piece is valid, the whole is not.
    private val inconsistent = consistent.copy(
        transactions = listOf(
            aTransaction(accountId = anAccountId("11111111-1111-1111-1111-111111111199"))
        )
    )

    @Test
    fun `reads the text it is given, and puts what it holds in place of everything`() = runTest()
    {
        // GIVEN
        serializer.onRead = { consistent }

        // WHEN
        service.import("the file")

        // THEN
        assertThat(serializer.read).containsExactly("the file")
        assertThat(restorer.restored).containsExactly(consistent)
    }

    @Test
    fun `a backup with nothing in it is accepted, and empties the app`() = runTest()
    {
        // GIVEN
        val empty = consistent.copy(
            accounts = emptyList(),
            subcategories = emptyList(),
            transactions = emptyList()
        )
        serializer.onRead = { empty }

        // WHEN
        service.import("an empty backup")

        // THEN
        assertThat(restorer.restored).containsExactly(empty)
    }

    @Test
    fun `a file that cannot be read is refused, and nothing is replaced`() = runTest()
    {
        // GIVEN
        serializer.onRead = { throw InvalidBackupException() }

        // WHEN / THEN
        assertThatThrownBySuspending { service.import("not a backup") }
            .isInstanceOf(InvalidBackupException::class.java)
        assertThat(restorer.restored).isEmpty()
    }

    @Test
    fun `a file whose pieces disagree is refused, and nothing is replaced`() = runTest()
    {
        // GIVEN
        serializer.onRead = { inconsistent }

        // WHEN / THEN
        assertThatThrownBySuspending { service.import("a broken backup") }
            .isInstanceOf(InvalidBackupException::class.java)
        assertThat(restorer.restored).isEmpty()
    }

    @Test
    fun `a failure of the storage is let through`() = runTest()
    {
        // GIVEN
        serializer.onRead = { consistent }
        restorer.failure = IllegalStateException("the disk is full")

        // WHEN / THEN
        assertThatThrownBySuspending { service.import("the file") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `an expense and its transfer legs are no special case`() = runTest()
    {
        // GIVEN
        val withTransfer = consistent.copy(
            transactions = listOf(
                aTransaction(accountId = account.id, category = TransactionCategory.TRANSFER_OUT),
                aTransaction(
                    id = com.kyovo.cents.application.fakes.aTransactionId("33333333-3333-3333-3333-333333333332"),
                    accountId = account.id,
                    category = TransactionCategory.TRANSFER_IN
                )
            )
        )
        serializer.onRead = { withTransfer }

        // WHEN
        service.import("the file")

        // THEN
        assertThat(restorer.restored.single().transactions).hasSize(2)
    }

    @Test
    fun `importing twice replaces twice`() = runTest()
    {
        // GIVEN
        serializer.onRead = { consistent }

        // WHEN
        service.import("the file")
        service.import("the file")

        // THEN
        assertThat(restorer.restored).hasSize(2)
    }

    @Test
    fun `answers with what was restored, counted by kind`() = runTest()
    {
        // GIVEN one account, one subcategory, one transaction
        serializer.onRead = { consistent }

        // WHEN
        val summary = service.import("the file")

        // THEN
        assertThat(summary).isEqualTo(
            BackupSummary(
                accounts = 1,
                subcategories = 1,
                transactions = 1,
                budgets = 0,
                recurringTransactions = 0,
                projects = 0
            )
        )
        assertThat(summary.isEmpty).isFalse()
    }

    @Test
    fun `a backup with nothing in it answers with an empty summary`() = runTest()
    {
        // GIVEN
        serializer.onRead = {
            consistent.copy(accounts = emptyList(), subcategories = emptyList(), transactions = emptyList())
        }

        // WHEN
        val summary = service.import("an empty backup")

        // THEN: still accepted, and the caller can tell the app has been emptied
        assertThat(summary.isEmpty).isTrue()
        assertThat(restorer.restored).hasSize(1)
    }

    @Test
    fun `the summary describes what was put in place, not what was there before`() = runTest()
    {
        // GIVEN two imports of different sizes
        serializer.onRead = { consistent }
        service.import("first")
        serializer.onRead = { consistent.copy(transactions = emptyList()) }

        // WHEN
        val summary = service.import("second")

        // THEN
        assertThat(summary.transactions).isEqualTo(0)
        assertThat(summary.accounts).isEqualTo(1)
    }
}
