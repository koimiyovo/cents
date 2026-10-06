package com.kyovo.cents.infrastructure.backup

import com.kyovo.cents.domain.exception.InvalidBackupException
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountDescription
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.BackupSnapshot
import com.kyovo.cents.domain.model.Budget
import com.kyovo.cents.domain.model.BudgetCalendar
import com.kyovo.cents.domain.model.BudgetStartDay
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.RecurrenceFrequency
import com.kyovo.cents.domain.model.RecurringTransaction
import com.kyovo.cents.domain.model.RecurringTransactionId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import com.kyovo.cents.domain.port.output.BackupSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import java.util.UUID

/**
 * The backup file: a JSON text, written from a [BackupSnapshot] and read back into one.
 *
 * The file has its own plain-value types (below), apart from the domain's, for the same reason the
 * database rows do: renaming a domain class or enum constant must not silently change what an old file
 * means. Ids, instants and dates are text (an instant keeps its nanoseconds), money is a `Long` of
 * cents, enums are their constant's name.
 *
 * Reading goes back through the domain's own constructors, so a file holding what the domain would
 * refuse (a blank name, a limit of zero...) is refused whole with an [InvalidBackupException]. The one
 * exception is a transaction, rebuilt with [Transaction.restored] since a stored one may predate a rule.
 */
class JsonBackupSerializer : BackupSerializer
{
    override fun serialize(snapshot: BackupSnapshot): String
    {
        return json.encodeToString(BackupFile.serializer(), snapshot.toFile())
    }

    override fun deserialize(text: String): BackupSnapshot
    {
        try
        {
            val file = json.decodeFromString(BackupFile.serializer(), text)
            if (file.formatVersion != FORMAT_VERSION) throw InvalidBackupException()
            return file.toSnapshot()
        }
        catch (e: InvalidBackupException)
        {
            throw e
        }
        // SerializationException and the domain's exceptions are IllegalArgument/IllegalState;
        // a malformed date or currency is one of the other two.
        catch (e: IllegalArgumentException)
        {
            throw InvalidBackupException(e)
        }
        catch (e: IllegalStateException)
        {
            throw InvalidBackupException(e)
        }
        catch (e: DateTimeException)
        {
            throw InvalidBackupException(e)
        }
    }

    private companion object
    {
        const val FORMAT_VERSION = 1

        // A file with a field this version does not know comes from another version: refused, not ignored.
        val json = Json
    }
}

@Serializable
private class BackupFile(
    // First, and without a default, so that it is always written and a file without it is refused.
    val formatVersion: Int,
    val accounts: List<AccountFile>,
    val subcategories: List<SubcategoryFile>,
    val transactions: List<TransactionFile>,
    val budgets: List<BudgetFile>,
    val budgetCalendar: BudgetCalendarFile,
    val recurringTransactions: List<RecurringTransactionFile>,
    val projects: List<ProjectFile>
)

@Serializable
private class AccountFile(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val createdAt: String,
    val archivedAt: String? = null,
    val description: String? = null
)

@Serializable
private class SubcategoryFile(val id: String, val kind: String, val name: String, val emoji: String? = null)

@Serializable
private class TransactionFile(
    val id: String,
    val accountId: String,
    val amountCents: Long,
    val title: String,
    val category: String,
    val subcategoryId: String? = null,
    val description: String? = null,
    val date: String,
    val projectId: String? = null
)

@Serializable
private class BudgetFile(val subcategoryId: String, val month: String, val limitCents: Long, val alertPercent: Int)

@Serializable
private class BudgetCalendarFile(val defaultStartDay: Int, val declaredStarts: List<String>)

@Serializable
private class RecurringTransactionFile(
    val id: String,
    val accountId: String,
    val category: String,
    val amountCents: Long,
    val title: String,
    val subcategoryId: String? = null,
    val description: String? = null,
    val frequency: String,
    val interval: Int,
    val startDate: String,
    val endDate: String? = null,
    val lastGeneratedDate: String? = null
)

@Serializable
private class ProjectFile(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val targetCents: Long? = null,
    val alertPercent: Int
)

private fun BackupSnapshot.toFile() = BackupFile(
    formatVersion = 1,
    accounts = accounts.map {
        AccountFile(
            it.id.value.toString(), it.name.value, it.type.name, it.currency.value.currencyCode,
            it.createdAt.toString(), it.archivedAt?.toString(), it.description?.value
        )
    },
    subcategories = subcategories.map {
        SubcategoryFile(it.id.value.toString(), it.kind.name, it.name.value, it.emoji?.value)
    },
    transactions = transactions.map {
        TransactionFile(
            it.id.value.toString(), it.accountId.value.toString(), it.amount.value, it.title.value,
            it.category.name, it.subcategoryId?.value?.toString(), it.description?.value,
            it.date.toString(), it.projectId?.value?.toString()
        )
    },
    budgets = budgets.map {
        BudgetFile(it.subcategoryId.value.toString(), it.month.toString(), it.limit.value, it.alertThreshold.percent)
    },
    budgetCalendar = BudgetCalendarFile(
        budgetCalendar.defaultStartDay.value,
        budgetCalendar.declaredStarts.sorted().map { it.toString() }
    ),
    recurringTransactions = recurringTransactions.map {
        RecurringTransactionFile(
            it.id.value.toString(), it.accountId.value.toString(), it.category.name, it.amount.value,
            it.title.value, it.subcategoryId?.value?.toString(), it.description?.value, it.frequency.name,
            it.interval, it.startDate.toString(), it.endDate?.toString(), it.lastGeneratedDate?.toString()
        )
    },
    projects = projects.map {
        ProjectFile(
            it.id.value.toString(), it.name.value, it.emoji?.value, it.target?.value, it.alertThreshold.percent
        )
    }
)

private fun BackupFile.toSnapshot() = BackupSnapshot(
    accounts = accounts.map {
        Account(
            AccountId(UUID.fromString(it.id)),
            AccountName(it.name),
            AccountType.valueOf(it.type),
            AccountCurrency(Currency.getInstance(it.currency)),
            Instant.parse(it.createdAt),
            it.archivedAt?.let(Instant::parse),
            AccountDescription.of(it.description)
        )
    },
    subcategories = subcategories.map {
        Subcategory(
            SubcategoryId(UUID.fromString(it.id)),
            RecordableTransactionCategory.valueOf(it.kind),
            SubcategoryName(it.name),
            it.emoji?.let { emoji -> Emoji(emoji) }
        )
    },
    transactions = transactions.map {
        Transaction.restored(
            TransactionId(UUID.fromString(it.id)),
            AccountId(UUID.fromString(it.accountId)),
            Money(it.amountCents),
            TransactionTitle(it.title),
            TransactionCategory.valueOf(it.category),
            it.subcategoryId?.let { id -> SubcategoryId(UUID.fromString(id)) },
            TransactionDescription.of(it.description),
            Instant.parse(it.date),
            it.projectId?.let { id -> ProjectId(UUID.fromString(id)) }
        )
    },
    budgets = budgets.map {
        Budget(
            SubcategoryId(UUID.fromString(it.subcategoryId)),
            YearMonth.parse(it.month),
            Money(it.limitCents),
            AlertThreshold(it.alertPercent)
        )
    },
    budgetCalendar = BudgetCalendar(
        BudgetStartDay(budgetCalendar.defaultStartDay),
        budgetCalendar.declaredStarts.map(LocalDate::parse).toSet()
    ),
    recurringTransactions = recurringTransactions.map {
        RecurringTransaction(
            RecurringTransactionId(UUID.fromString(it.id)),
            AccountId(UUID.fromString(it.accountId)),
            RecordableTransactionCategory.valueOf(it.category),
            Money(it.amountCents),
            TransactionTitle(it.title),
            it.subcategoryId?.let { id -> SubcategoryId(UUID.fromString(id)) },
            TransactionDescription.of(it.description),
            RecurrenceFrequency.valueOf(it.frequency),
            it.interval,
            LocalDate.parse(it.startDate),
            it.endDate?.let(LocalDate::parse),
            it.lastGeneratedDate?.let(LocalDate::parse)
        )
    },
    projects = projects.map {
        Project(
            ProjectId(UUID.fromString(it.id)),
            ProjectName(it.name),
            it.emoji?.let { emoji -> Emoji(emoji) },
            it.targetCents?.let(::Money),
            AlertThreshold(it.alertPercent)
        )
    }
)
