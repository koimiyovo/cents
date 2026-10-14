package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.ExportTransactionsCsvUseCase
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.ProjectRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import java.time.ZoneId
import kotlin.math.abs

/**
 * Written for a French spreadsheet: a UTF-8 byte order mark (so accents survive), `;` between fields (the
 * comma is the decimal separator), a decimal comma, lines ended by CRLF.
 */
class ExportTransactionsCsvService(
    private val accountRepository: AccountRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val projectRepository: ProjectRepository,
    private val unitOfWork: UnitOfWork,
    private val zone: ZoneId
) : ExportTransactionsCsvUseCase
{
    private companion object
    {
        const val BOM = "﻿"
        const val SEPARATOR = ";"
        const val NEW_LINE = "\r\n"
        val HEADER = listOf("Date", "Compte", "Type", "Titre", "Montant", "Devise", "Sous-catégorie", "Projet", "Description")
    }

    override suspend fun export(): String
    {
        return unitOfWork.execute {
            val accounts = accountRepository.findAll().associateBy { it.id }
            val subcategories = subcategoryRepository.findAll().associate { it.id to it.name.value }
            val projects = projectRepository.findAll().associate { it.id to it.name.value }

            // sortedBy is stable: transactions of the same instant keep their stored order.
            val rows = transactionRepository.findAll().sortedBy { it.date }.map {
                listOf(
                    it.date.atZone(zone).toLocalDate().toString(),
                    accounts[it.accountId]?.name?.value.orEmpty(),
                    label(it.category),
                    it.title.value,
                    formatAmount(it, accounts[it.accountId]?.currency),
                    accounts[it.accountId]?.currency?.value?.currencyCode.orEmpty(),
                    it.subcategoryId?.let(subcategories::get).orEmpty(),
                    it.projectId?.let(projects::get).orEmpty(),
                    it.description?.value.orEmpty()
                )
            }

            (listOf(HEADER) + rows).joinToString(separator = "", prefix = BOM) {
                it.joinToString(SEPARATOR, transform = ::escape) + NEW_LINE
            }
        }
    }

    private fun label(category: TransactionCategory): String = when (category)
    {
        TransactionCategory.EXPENSE         -> "Dépense"
        TransactionCategory.INCOME          -> "Revenu"
        TransactionCategory.INITIAL_DEPOSIT -> "Dépôt initial"
        TransactionCategory.TRANSFER_OUT    -> "Virement sortant"
        TransactionCategory.TRANSFER_IN     -> "Virement entrant"
    }

    /** The amount with its currency's decimals (none for the CFA franc); two when the account is unknown. */
    private fun formatAmount(transaction: Transaction, currency: AccountCurrency?): String
    {
        val units = transaction.signedAmount
        val sign = if (units < 0) "-" else ""
        val digits = currency?.fractionDigits ?: 2
        if (digits == 0) return "$sign${abs(units)}"
        var factor = 1L
        repeat(digits) { factor *= 10 }
        return "$sign${abs(units) / factor},${(abs(units) % factor).toString().padStart(digits, '0')}"
    }

    private fun escape(field: String): String
    {
        return if (field.any { it == ';' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field
    }
}
