package com.kyovo.cents.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.Transaction
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.input.ListProjectsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.budget.SpendingLegendRow
import com.kyovo.cents.ui.budget.SpendingPie
import com.kyovo.cents.ui.common.SelectDropdown
import com.kyovo.cents.ui.common.SelectOption
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * The "Projets" half of the Budget screen: one project at a time, chosen from a dropdown (the project of the latest
 * transaction at first, then the user's choice), with its card, where its money went, and its biggest expenses.
 * A link creates a project, which the tab then shows (it has no transaction yet, so it would never be the default).
 * Its children go straight into the parent column. Nothing is month-bound, so none of the month's controls apply.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectAnalysisTab(
    palette: AccountsPalette,
    listProjects: ListProjectsUseCase,
    getProjectProgress: GetProjectProgressUseCase,
    listTransactions: ListTransactionsUseCase,
    subcategories: List<Subcategory>,
    // The project the user picked, held by the caller (as the id's string, which survives rotation and a trip to
    // the project's page): null until they pick one.
    chosenProjectUuid: String?,
    onChooseProject: (ProjectId) -> Unit,
    onOpenProject: (ProjectId) -> Unit,
    onCreateProject: () -> Unit,
)
{
    // Null while the first list is on its way: that is "not loaded yet", not "no project".
    val projects by remember { listProjects.observe() }.collectAsStateWithLifecycle(initialValue = null)
    val progress by remember { getProjectProgress.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val allTransactions by remember { listTransactions.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())

    // The projects that existed when the user asked to create one: the new one is the one that is not among them.
    // Kept here, not saved: leaving the tab forgets it, so a project created from elsewhere later is not picked.
    var idsWhenAsked by remember { mutableStateOf<Set<ProjectId>?>(null) }
    LaunchedEffect(projects) {
        val asked = idsWhenAsked ?: return@LaunchedEffect
        val created = newlyCreatedProject(asked, projects ?: return@LaunchedEffect) ?: return@LaunchedEffect
        onChooseProject(created.id)
        idsWhenAsked = null
    }
    val createProject = {
        idsWhenAsked = projects.orEmpty().map { it.id }.toSet()
        onCreateProject()
    }

    val loaded = projects ?: return
    if (loaded.isEmpty())
    {
        Text(text = stringResource(R.string.project_analysis_no_project), color = palette.textMuted, fontSize = 14.sp)
        LinkPill(palette, "+ " + stringResource(R.string.projects_new), createProject)
        return
    }

    val chosen = chosenProjectUuid?.let { ProjectId(UUID.fromString(it)) }
    val selected = resolveSelectedProject(chosen, loaded, allTransactions) ?: return
    val transactions = remember(allTransactions, selected.id) { transactionsOfProject(allTransactions, selected.id) }

    ProjectPicker(palette, loaded, selected) { onChooseProject(it.id) }
    ProjectCardView(palette, projectCards(listOf(selected), progress).single())
    // Side by side when they fit, one under the other on a narrow screen.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkPill(palette, stringResource(R.string.project_analysis_open)) { onOpenProject(selected.id) }
        LinkPill(palette, "+ " + stringResource(R.string.projects_new), createProject)
    }

    if (transactions.isEmpty())
    {
        Text(text = stringResource(R.string.project_details_empty), color = palette.textMuted, fontSize = 14.sp)
        return
    }

    BreakdownSection(palette, projectBreakdown(transactions, subcategories))
    TopExpensesSection(palette, topExpenses(transactions))
}

/** A small rounded link: an action that is not the main content of the screen. */
@Composable
private fun LinkPill(palette: AccountsPalette, text: String, onClick: () -> Unit)
{
    Text(
        text = text,
        color = palette.kicker,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ProjectPicker(palette: AccountsPalette, projects: List<Project>, selected: Project, onSelect: (Project) -> Unit)
{
    SelectDropdown(
        palette = palette,
        options = projects.map { SelectOption(it, "${it.emoji?.value ?: DEFAULT_PROJECT_EMOJI} ${it.name.value}") },
        selected = selected,
        onSelect = onSelect,
        fillWidth = true,
    )
}

@Composable
private fun SectionTitle(palette: AccountsPalette, text: String)
{
    Text(text = text, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

/** The pie of the expenses by subcategory, its legend, and — apart — what refunds took off. */
@Composable
private fun BreakdownSection(palette: AccountsPalette, breakdown: ProjectBreakdown)
{
    SectionTitle(palette, stringResource(R.string.project_analysis_breakdown_title))
    if (breakdown.spending.slices.isEmpty())
    {
        Text(text = stringResource(R.string.project_analysis_breakdown_empty), color = palette.textMuted, fontSize = 14.sp)
    } else
    {
        SpendingPie(palette, breakdown.spending)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            breakdown.spending.slices.forEachIndexed { index, slice -> SpendingLegendRow(palette, index, slice) }
        }
    }
    if (breakdown.refunds.isNotZero())
    {
        Text(
            text = stringResource(R.string.project_analysis_refunds, formatEuroCents(breakdown.refunds.value)),
            color = palette.textSecondary,
            fontSize = 14.sp,
        )
    }
}

/** The few biggest expenses: title, day and amount. */
@Composable
private fun TopExpensesSection(palette: AccountsPalette, expenses: List<Transaction>)
{
    if (expenses.isEmpty()) return
    SectionTitle(palette, stringResource(R.string.project_analysis_top_title))
    val dayFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface),
    ) {
        expenses.forEachIndexed { index, expense ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = expense.title.value, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    val day: LocalDate = expense.date.atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(text = day.format(dayFormat), color = palette.textMuted, fontSize = 12.sp)
                }
                Text(
                    text = formatEuroCents(expense.amount.value),
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (index != expenses.lastIndex)
            {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            }
        }
    }
}
