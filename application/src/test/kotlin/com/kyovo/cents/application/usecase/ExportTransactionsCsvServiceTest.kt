package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryAccountRepository
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.InMemorySubcategoryRepository
import com.kyovo.cents.application.fakes.InMemoryTransactionRepository
import com.kyovo.cents.application.fakes.InMemoryUnitOfWork
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aSubcategory
import com.kyovo.cents.application.fakes.aSubcategoryId
import com.kyovo.cents.application.fakes.aTransaction
import com.kyovo.cents.application.fakes.aTransactionId
import com.kyovo.cents.application.fakes.aTransactionTitle
import com.kyovo.cents.application.fakes.anAccount
import com.kyovo.cents.application.fakes.anAccountId
import com.kyovo.cents.application.fakes.anInstant
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.model.TransactionDescription
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * The CSV is meant to be opened in a spreadsheet by a French user: a UTF-8 byte order mark (so accents
 * survive), `;` between fields (the comma is the decimal separator), a decimal comma, lines ended by CRLF.
 * One line per transaction, oldest first, every account included (archived ones too).
 */
class ExportTransactionsCsvServiceTest
{
    private val accounts = InMemoryAccountRepository()
    private val subcategories = InMemorySubcategoryRepository()
    private val transactions = InMemoryTransactionRepository()
    private val projects = InMemoryProjectRepository()
    private val paris = ZoneId.of("Europe/Paris")

    private val bom = "﻿"
    private val header = "Date;Compte;Type;Titre;Montant;Sous-catégorie;Projet;Description"

    private fun service(zone: ZoneId = paris) = ExportTransactionsCsvService(
        accounts,
        subcategories,
        transactions,
        projects,
        InMemoryUnitOfWork(),
        zone
    )

    /** The lines after the header (the text is BOM + header + one CRLF-ended line per transaction). */
    private suspend fun lines(zone: ZoneId = paris): List<String>
    {
        val text = service(zone).export()
        assertThat(text).startsWith(bom + header + "\r\n")
        assertThat(text).endsWith("\r\n")
        return text.removePrefix(bom).split("\r\n").dropLast(1).drop(1)
    }

    @Test
    fun `an app with no transaction exports the header alone`() = runTest()
    {
        assertThat(service().export()).isEqualTo(bom + header + "\r\n")
    }

    @Test
    fun `a transaction is one line, the amount signed with a decimal comma`() = runTest()
    {
        // GIVEN
        val account = anAccount(name = AccountName("Courant"))
        accounts.save(account)
        transactions.save(
            aTransaction(
                accountId = account.id,
                category = TransactionCategory.EXPENSE,
                title = aTransactionTitle("Restaurant"),
                amount = aMoney(3_800),
                date = anInstant("2026-09-22T10:00:00Z")
            )
        )

        // THEN
        assertThat(lines()).containsExactly("2026-09-22;Courant;Dépense;Restaurant;-38,00;;;")
    }

    @Test
    fun `an income is positive and thousands are not separated`() = runTest()
    {
        // GIVEN
        val account = anAccount(name = AccountName("Courant"))
        accounts.save(account)
        transactions.save(
            aTransaction(
                accountId = account.id,
                category = TransactionCategory.INCOME,
                title = aTransactionTitle("Salaire"),
                amount = aMoney(123_456_05)
            )
        )

        // THEN
        assertThat(lines().single()).contains(";Revenu;Salaire;123456,05;")
    }

    @Test
    fun `each kind of transaction has its own French label`() = runTest()
    {
        // GIVEN
        val account = anAccount()
        accounts.save(account)
        val kinds = TransactionCategory.entries
        kinds.forEachIndexed { i, category ->
            transactions.save(
                aTransaction(
                    id = aTransactionId("33333333-3333-3333-3333-33333333333$i"),
                    accountId = account.id,
                    category = category
                )
            )
        }

        // THEN
        val labels = lines().map { it.split(";")[2] }
        assertThat(labels).containsExactlyInAnyOrder(
            "Dépense",
            "Revenu",
            "Dépôt initial",
            "Virement sortant",
            "Virement entrant"
        )
    }

    @Test
    fun `subcategory, project and description are written when there are some`() = runTest()
    {
        // GIVEN
        val account = anAccount(name = AccountName("Courant"))
        val subcategory = aSubcategory(name = SubcategoryName("Alimentation"))
        val project = aProject(name = ProjectName("Voyage au Japon"))
        accounts.save(account)
        subcategories.save(subcategory)
        projects.save(project)
        transactions.save(
            aTransaction(
                accountId = account.id,
                category = TransactionCategory.EXPENSE,
                title = aTransactionTitle("Courses"),
                amount = aMoney(1_250),
                subcategoryId = subcategory.id,
                projectId = project.id,
                description = TransactionDescription.of("Marché du samedi")
            )
        )

        // THEN
        assertThat(lines().single())
            .endsWith(";Courses;-12,50;Alimentation;Voyage au Japon;Marché du samedi")
    }

    @Test
    fun `a field with a semicolon, a quote or a line break is quoted, quotes doubled`() = runTest()
    {
        // GIVEN
        val account = anAccount(name = AccountName("Courant"))
        accounts.save(account)
        transactions.save(
            aTransaction(
                accountId = account.id,
                category = TransactionCategory.EXPENSE,
                title = aTransactionTitle("Pain; \"bio\""),
                description = TransactionDescription.of("ligne 1\nligne 2")
            )
        )

        // THEN
        val text = service().export()
        assertThat(text).contains(";\"Pain; \"\"bio\"\"\";")
        assertThat(text).endsWith(";\"ligne 1\nligne 2\"\r\n")
    }

    @Test
    fun `transactions come oldest first, whatever the order they were stored in`() = runTest()
    {
        // GIVEN
        val account = anAccount()
        accounts.save(account)
        listOf("2026-09-25T10:00:00Z", "2026-09-01T10:00:00Z", "2026-09-10T10:00:00Z")
            .forEachIndexed { i, date ->
                transactions.save(
                    aTransaction(
                        id = aTransactionId("33333333-3333-3333-3333-33333333333$i"),
                        accountId = account.id,
                        date = anInstant(date)
                    )
                )
            }

        // THEN
        assertThat(lines().map { it.substringBefore(";") })
            .containsExactly("2026-09-01", "2026-09-10", "2026-09-25")
    }

    @Test
    fun `transactions of the same instant keep the order they were stored in`() = runTest()
    {
        // GIVEN
        val account = anAccount()
        accounts.save(account)
        listOf("Premier", "Deuxième", "Troisième").forEachIndexed { i, title ->
            transactions.save(
                aTransaction(
                    id = aTransactionId("33333333-3333-3333-3333-33333333333$i"),
                    accountId = account.id,
                    category = TransactionCategory.EXPENSE,
                    title = aTransactionTitle(title)
                )
            )
        }

        // THEN
        assertThat(lines().map { it.split(";")[3] }).containsExactly("Premier", "Deuxième", "Troisième")
    }

    @Test
    fun `the date is the day in the user's zone, not in UTC`() = runTest()
    {
        // GIVEN
        val account = anAccount()
        accounts.save(account)
        transactions.save(aTransaction(accountId = account.id, date = anInstant("2026-09-30T22:30:00Z")))

        // THEN
        assertThat(lines(paris).single()).startsWith("2026-10-01;")
        assertThat(lines(ZoneId.of("UTC")).single()).startsWith("2026-09-30;")
    }

    @Test
    fun `the transactions of every account are exported, archived ones included`() = runTest()
    {
        // GIVEN
        val open = anAccount(id = anAccountId("11111111-1111-1111-1111-111111111111"), name = AccountName("Courant"))
        val closed = anAccount(
            id = anAccountId("11111111-1111-1111-1111-111111111112"),
            name = AccountName("Ancien"),
            archivedAt = anInstant("2026-01-01T00:00:00Z")
        )
        accounts.save(open)
        accounts.save(closed)
        transactions.save(aTransaction(id = aTransactionId("33333333-3333-3333-3333-333333333331"), accountId = open.id))
        transactions.save(aTransaction(id = aTransactionId("33333333-3333-3333-3333-333333333332"), accountId = closed.id))

        // THEN
        assertThat(lines().map { it.split(";")[1] }).containsExactly("Courant", "Ancien")
    }

    @Test
    fun `a subcategory that no longer exists is left blank rather than failing`() = runTest()
    {
        // GIVEN: a database would not allow it, but an export must not be the thing that breaks
        val account = anAccount()
        accounts.save(account)
        transactions.save(
            aTransaction(
                accountId = account.id,
                category = TransactionCategory.EXPENSE,
                subcategoryId = aSubcategoryId()
            )
        )

        // THEN
        assertThat(lines().single().split(";")[5]).isEmpty()
    }

    @Test
    fun `exporting changes nothing`() = runTest()
    {
        // GIVEN
        val account = anAccount()
        val transaction = aTransaction(accountId = account.id)
        accounts.save(account)
        transactions.save(transaction)

        // WHEN
        service().export()

        // THEN
        assertThat(accounts.saved).containsExactly(account)
        assertThat(transactions.findAll()).containsExactly(transaction)
    }
}
