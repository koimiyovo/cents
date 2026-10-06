package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.AccountId
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.model.TransactionId
import com.kyovo.cents.domain.model.TransactionTitle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val JAPAN = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666661"))
private val KITCHEN = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666662"))

private fun aProject(id: ProjectId, name: String, emoji: String? = null, target: Long? = null) =
    Project(id, ProjectName(name), emoji?.let { Emoji(it) }, target?.let { Money(it) })

private fun progress(expenses: Long, incomes: Long = 0, target: Long? = null, count: Int = 1) =
    ProjectProgress(target?.let { Money(it) }, Money(expenses), Money(incomes), count)

private fun aCard(expenses: Long, incomes: Long = 0, target: Long? = null) =
    ProjectCard(aProject(JAPAN, "Voyage au Japon", target = target), progress(expenses, incomes, target))

/**
 * What the projects screen shows for a project: the project, what it has cost so far (net of refunds),
 * and — when it has a target — how far along it is. The cards keep the order of the projects given
 * (the use case already sorts them by name).
 */
class ProjectCardsTest
{
    // ------------------------------------------------------------------ the list

    @Test
    fun `there is one card per project, in the order given, with its progress`()
    {
        // GIVEN
        val japan = aProject(JAPAN, "Voyage au Japon", target = 300_000)
        val kitchen = aProject(KITCHEN, "Travaux cuisine")
        val progressById = mapOf(
            JAPAN to progress(expenses = 120_000, target = 300_000, count = 4),
            KITCHEN to progress(expenses = 5_000, count = 1),
        )

        // WHEN
        val cards = projectCards(listOf(kitchen, japan), progressById)

        // THEN
        assertThat(cards.map { it.project }).containsExactly(kitchen, japan)
        assertThat(cards.map { it.progress }).containsExactly(progressById[KITCHEN], progressById[JAPAN])
    }

    @Test
    fun `a project whose progress is not known yet shows as empty`()
    {
        // GIVEN a project the progress flow has not caught up with (the two flows emit separately)
        val japan = aProject(JAPAN, "Voyage au Japon", target = 300_000)

        // WHEN
        val card = projectCards(listOf(japan), emptyMap()).single()

        // THEN nothing spent, no transaction, and its own target
        assertThat(card.progress).isEqualTo(progress(expenses = 0, target = 300_000, count = 0))
    }

    @Test
    fun `there is no card without a project`()
    {
        assertThat(projectCards(emptyList(), mapOf(JAPAN to progress(1_000)))).isEmpty()
    }

    // ------------------------------------------------------------------ the bar

    @Test
    fun `a project without a target has no bar`()
    {
        assertThat(aCard(expenses = 50_000).barFraction).isNull()
    }

    @Test
    fun `the bar is the share of the target already spent`()
    {
        assertThat(aCard(expenses = 75_000, target = 300_000).barFraction).isEqualTo(0.25f)
    }

    @Test
    fun `a refund shortens the bar, since it lowers what the project cost`()
    {
        assertThat(aCard(expenses = 100_000, incomes = 25_000, target = 300_000).barFraction).isEqualTo(0.25f)
    }

    @Test
    fun `the bar stays full once over the target`()
    {
        assertThat(aCard(expenses = 450_000, target = 300_000).barFraction).isEqualTo(1f)
    }

    @Test
    fun `the bar is empty when the refunds exceed the expenses`()
    {
        assertThat(aCard(expenses = 10_000, incomes = 12_000, target = 300_000).barFraction).isEqualTo(0f)
    }

    @Test
    fun `the bar is full at exactly the target`()
    {
        assertThat(aCard(expenses = 300_000, target = 300_000).barFraction).isEqualTo(1f)
    }

    // ------------------------------------------------------------------ over the target

    @Test
    fun `a card is over its target only above it`()
    {
        assertThat(aCard(expenses = 300_001, target = 300_000).isOverTarget).isTrue()
        assertThat(aCard(expenses = 300_000, target = 300_000).isOverTarget).isFalse()
        assertThat(aCard(expenses = 999_999).isOverTarget).isFalse()
    }

    @Test
    fun `a card tells what is left, or by how much it is over`()
    {
        assertThat(aCard(expenses = 120_000, target = 300_000).remaining).isEqualTo(ProjectRemaining.Left(180_000))
        assertThat(aCard(expenses = 300_000, target = 300_000).remaining).isEqualTo(ProjectRemaining.Left(0))
        assertThat(aCard(expenses = 320_000, target = 300_000).remaining).isEqualTo(ProjectRemaining.Over(20_000))
    }

    @Test
    fun `a card without a target has nothing left to tell`()
    {
        assertThat(aCard(expenses = 120_000).remaining).isNull()
    }

    // ------------------------------------------------------------------ what the card shows

    @Test
    fun `the net cost and the number of transactions come from the progress`()
    {
        val card = ProjectCard(aProject(JAPAN, "Voyage"), progress(expenses = 100_000, incomes = 15_000, count = 6))

        assertThat(card.netCents).isEqualTo(85_000)
        assertThat(card.transactionCount).isEqualTo(6)
    }

    @Test
    fun `the icon is the project's own emoji, or a default - never blank`()
    {
        val own = ProjectCard(aProject(JAPAN, "Voyage", "✈️"), progress(0))
        val none = ProjectCard(aProject(KITCHEN, "Travaux"), progress(0))

        assertThat(own.displayEmoji()).isEqualTo("✈️")
        assertThat(none.displayEmoji()).isEqualTo(DEFAULT_PROJECT_EMOJI).isNotBlank()
    }

    // ------------------------------------------------------------------ the transactions of a project

    private fun anExpense(suffix: Int, projectId: ProjectId?, category: RecordableTransactionCategory = RecordableTransactionCategory.EXPENSE) =
        Transaction.recorded(
            TransactionId(UUID.fromString("33333333-3333-3333-3333-33333333333$suffix")),
            AccountId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            Money(1_000), TransactionTitle("Achat $suffix"), category, null, null,
            Instant.parse("2026-09-22T10:00:00Z"), projectId,
        )

    @Test
    fun `the transactions of a project are those that belong to it, in the order given`()
    {
        // GIVEN
        val first = anExpense(1, JAPAN)
        val other = anExpense(2, KITCHEN)
        val none = anExpense(3, null)
        val refund = anExpense(4, JAPAN, RecordableTransactionCategory.INCOME)

        // WHEN / THEN a refund counts as one of them
        assertThat(transactionsOfProject(listOf(first, other, none, refund), JAPAN)).containsExactly(first, refund)
    }

    @Test
    fun `a project without transactions has none`()
    {
        assertThat(transactionsOfProject(listOf(anExpense(1, KITCHEN)), JAPAN)).isEmpty()
    }
}
