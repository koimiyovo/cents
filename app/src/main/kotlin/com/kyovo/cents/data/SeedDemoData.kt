package com.kyovo.cents.data

import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

/**
 * Puts the demo data in, but only into a completely empty database: with a real database the data is
 * still there at the next launch, and seeding again would duplicate it or bury the user's own. It is
 * all-or-nothing (a unit of work), so a launch killed halfway leaves a database that is still empty
 * and gets seeded properly at the next one. Gives back whether it seeded anything.
 *
 * Called for debug builds only: a release build starts empty, for the user to fill.
 */
suspend fun seedDemoDataIfEmpty(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    subcategoryRepository: SubcategoryRepository,
    unitOfWork: UnitOfWork,
): Boolean
{
    val empty = accountRepository.findAll().isEmpty() &&
            subcategoryRepository.findAll().isEmpty() &&
            transactionRepository.findAll().isEmpty()
    if (!empty) return false

    unitOfWork.execute { seedHardcodedData(accountRepository, transactionRepository, subcategoryRepository) }
    return true
}
