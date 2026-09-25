package com.kyovo.cents.ui.subcategory

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.port.input.ListSubcategoriesUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.ChevronDownIcon
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * Where the user manages their subcategories: expenses and incomes in two sections, each row showing
 * how many transactions use the subcategory, each section ending with its own "new subcategory".
 * Touching a row opens its edit form (where it can also be deleted).
 */
@Composable
fun SubcategoriesScreen(
    listSubcategories: ListSubcategoriesUseCase,
    listTransactions: ListTransactionsUseCase,
    revision: Int,
    onBack: () -> Unit,
    onCreate: (RecordableTransactionCategory) -> Unit,
    onEdit: (SubcategoryRow) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val sections = remember(revision) { subcategorySections(listSubcategories.list(), listTransactions.list()) }
    // Each section folds on its own: with many subcategories in the first, the second would otherwise be
    // lost at the bottom. Both start unfolded.
    var expensesExpanded by rememberSaveable { mutableStateOf(true) }
    var incomeExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(palette, onBack)
            Spacer(Modifier.width(12.dp))
            HomeTopBar(palette, stringResource(R.string.subcategories_title))
        }
        Text(
            text = stringResource(R.string.subcategories_intro),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
        sections.forEach { section ->
            val isExpense = section.kind == RecordableTransactionCategory.EXPENSE
            SectionBlock(
                palette = palette,
                section = section,
                expanded = if (isExpense) expensesExpanded else incomeExpanded,
                onToggle = { if (isExpense) expensesExpanded = !expensesExpanded else incomeExpanded = !incomeExpanded },
                onCreate = { onCreate(section.kind) },
                onEdit = onEdit,
            )
        }
    }
}

@Composable
private fun SectionBlock(
    palette: AccountsPalette,
    section: SubcategorySection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (SubcategoryRow) -> Unit,
)
{
    val isExpense = section.kind == RecordableTransactionCategory.EXPENSE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The action to add sits in the header, always in view: at the end of the list it would take a
        // long scroll to reach once there are many subcategories.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The title folds and unfolds the section; the count stays visible when it is folded.
            val toggleLabel = stringResource(R.string.subcategories_section_toggle)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = toggleLabel, onClick = onToggle)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChevronDownIcon(
                    tint = palette.textSecondary,
                    modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else -90f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isExpense) R.string.subcategories_section_expenses else R.string.subcategories_section_income,
                    ),
                    color = palette.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = section.rows.size.toString(),
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(palette.badgeBackground)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
            val newDescription = stringResource(
                if (isExpense) R.string.new_subcategory_title_expense else R.string.new_subcategory_title_income,
            )
            // A "+" alone says it: the accessible name carries the words.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(onClick = onCreate)
                    .semantics { contentDescription = newDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.subcategories_new),
                    color = palette.kicker,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.surface),
            ) {
                section.rows.forEachIndexed { index, row ->
                    SubcategoryRowItem(palette, row, onClick = { onEdit(row) })
                    if (index != section.rows.lastIndex)
                    {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                    }
                }
                if (section.rows.isEmpty())
                {
                    Text(
                        text = stringResource(
                            if (isExpense) R.string.subcategories_empty_expenses else R.string.subcategories_empty_income,
                        ),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubcategoryRowItem(palette: AccountsPalette, row: SubcategoryRow, onClick: () -> Unit)
{
    val editLabel = stringResource(R.string.subcategories_edit_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = editLabel, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(palette.background),
            contentAlignment = Alignment.Center,
        ) {
            // The category's own emoji stands in for a subcategory that has none.
            Text(text = row.displayEmoji(), fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.subcategory.name.value,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(text = transactionCountLabel(row.transactionCount), color = palette.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun transactionCountLabel(count: Int): String =
    if (count == 0) stringResource(R.string.subcategories_transactions_none)
    else pluralStringResource(R.plurals.subcategories_transactions, count, count)
