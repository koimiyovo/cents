package com.kyovo.cents.ui.home

import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.AccountName
import com.kyovo.cents.domain.model.AccountType
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import com.kyovo.cents.ui.project.DEFAULT_PROJECT_EMOJI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Currency
import java.util.UUID

private val RESTAURANTS = Subcategory(
    SubcategoryId(UUID.randomUUID()), RecordableTransactionCategory.EXPENSE, SubcategoryName("Restaurants"), null,
)
private val CHECKING = Account(
    AccountId(UUID.randomUUID()), AccountName("Compte courant"), AccountType.CHECKING,
    AccountCurrency(Currency.getInstance("EUR")), Instant.parse("2026-01-01T00:00:00Z"),
)
private val JAPAN = Project(ProjectId(UUID.randomUUID()), ProjectName("Voyage au Japon"), Emoji("✈️"), null)
private val KITCHEN = Project(ProjectId(UUID.randomUUID()), ProjectName("Travaux cuisine"), null, null)

/**
 * The second line of a transaction row: its subcategory, its account and its project, in that order, with the
 * project shown by its emoji and name (the project's own, or the default folder) so it reads at a glance.
 * What a row does not have is left out; a row with none of them has no second line.
 */
class TransactionSubtitleTest
{
    @Test
    fun `shows the subcategory, the account and the project, in that order`()
    {
        assertThat(transactionSubtitle(RESTAURANTS, CHECKING, JAPAN))
            .isEqualTo("Restaurants • Compte courant • ✈️ Voyage au Japon")
    }

    @Test
    fun `a project without an emoji shows the default one`()
    {
        assertThat(transactionSubtitle(null, null, KITCHEN)).isEqualTo("$DEFAULT_PROJECT_EMOJI Travaux cuisine")
    }

    @Test
    fun `leaves out what the row does not have`()
    {
        assertThat(transactionSubtitle(RESTAURANTS, null, null)).isEqualTo("Restaurants")
        assertThat(transactionSubtitle(null, CHECKING, null)).isEqualTo("Compte courant")
        assertThat(transactionSubtitle(RESTAURANTS, null, JAPAN)).isEqualTo("Restaurants • ✈️ Voyage au Japon")
        assertThat(transactionSubtitle(null, CHECKING, JAPAN)).isEqualTo("Compte courant • ✈️ Voyage au Japon")
    }

    @Test
    fun `a row with none of them has no second line`()
    {
        assertThat(transactionSubtitle(null, null, null)).isEmpty()
    }

    // The existing rows of the Transactions tab keep reading as they did.
    @Test
    fun `without a project the line is what it was`()
    {
        assertThat(transactionSubtitle(RESTAURANTS, CHECKING, null)).isEqualTo("Restaurants • Compte courant")
    }
}
