package com.kyovo.cents.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectProgress
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.input.ListProjectsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.DayGroup
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette
import com.kyovo.cents.ui.home.groupByDay
import com.kyovo.cents.ui.home.movementsCountLabel

/**
 * A project's page: its card (cost, target, what is left), then every transaction filed under it, by day,
 * the same rows as the Transactions tab - a tap edits one. "Modifier" opens the project's form, which is
 * also where it is deleted: the page then goes back to the list by itself, as the project is gone.
 */
@Composable
fun ProjectDetailsScreen(
    projectId: ProjectId,
    listProjects: ListProjectsUseCase,
    getProjectProgress: GetProjectProgressUseCase,
    listTransactions: ListTransactionsUseCase,
    accounts: List<Account>,
    subcategories: List<Subcategory>,
    onBack: () -> Unit,
    onEdit: (ProjectCard) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    // Null while the first list is on its way: that is "not loaded yet", not "no such project".
    val projects by remember { listProjects.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val progress by remember(projectId) { getProjectProgress.observe(projectId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val allTransactions by remember { listTransactions.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())

    val loaded = projects ?: run {
        Box(modifier = modifier.fillMaxSize().background(palette.background))
        return
    }
    val project = loaded.find { it.id == projectId }
    if (project == null)
    {
        // Deleted (from this very page, or elsewhere): nothing left to show, go back to the list.
        LaunchedEffect(Unit) { onBack() }
        Box(modifier = modifier.fillMaxSize().background(palette.background))
        return
    }

    val card = ProjectCard(project, progress ?: ProjectProgress(project.target, Money(0), Money(0), 0))
    val transactions = remember(allTransactions, projectId) { transactionsOfProject(allTransactions, projectId) }
    val groupedByDay = remember(transactions) { groupByDay(transactions) }
    val accountsById = remember(accounts) { accounts.associateBy { it.id } }
    val subcategoriesById = remember(subcategories) { subcategories.associateBy { it.id } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                HomeTopBar(palette, project.name.value)
            }
            Text(
                text = stringResource(R.string.project_details_edit),
                color = palette.kicker,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.surface)
                    .clickable { onEdit(card) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        ProjectCardView(palette, card)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.project_details_transactions_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = movementsCountLabel(transactions.size), color = palette.textMuted, fontSize = 13.sp)
        }
        if (groupedByDay.isEmpty())
        {
            Text(
                text = stringResource(R.string.project_details_empty),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else
        {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                groupedByDay.forEach { (date, dayTransactions) ->
                    DayGroup(
                        palette,
                        date,
                        dayTransactions,
                        accountsById = accountsById,
                        subcategoriesById = subcategoriesById,
                        onTransactionClick = onTransactionClick,
                    )
                }
            }
        }
    }
}
