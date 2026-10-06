package com.kyovo.cents.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.domain.port.input.GetProjectProgressUseCase
import com.kyovo.cents.domain.port.input.ListProjectsUseCase
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.home.AccountsPalette
import com.kyovo.cents.ui.home.BackButton
import com.kyovo.cents.ui.home.DarkAccountsPalette
import com.kyovo.cents.ui.home.HomeTopBar
import com.kyovo.cents.ui.home.LightAccountsPalette

/**
 * The projects: one card each, with what it has cost so far and, when it has a target, a bar showing how far
 * along it is. Touching a card opens the project's page (its transactions, and its edit and delete actions).
 * "New project" is always in the header, in view.
 */
@Composable
fun ProjectsScreen(
    listProjects: ListProjectsUseCase,
    getProjectProgress: GetProjectProgressUseCase,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (ProjectCard) -> Unit,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    val projects by remember { listProjects.observe() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by remember { getProjectProgress.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val cards = remember(projects, progress) { projectCards(projects, progress) }

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
                HomeTopBar(palette, stringResource(R.string.projects_title))
            }
            Text(
                text = "+ " + stringResource(R.string.projects_new),
                color = palette.kicker,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.surface)
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Text(text = stringResource(R.string.projects_intro), color = palette.textMuted, fontSize = 13.sp)
        if (cards.isEmpty())
        {
            Text(
                text = stringResource(R.string.projects_empty),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        cards.forEach { card -> ProjectCardView(palette, card, onClick = { onOpen(card) }) }
    }
}

/**
 * A project's card: its emoji and name, the number of transactions, what it has cost, and — with a target —
 * the bar, the target and what is left (or by how much it is passed, in the error colour). Shared by the
 * projects list and by a project's own page.
 */
@Composable
internal fun ProjectCardView(
    palette: AccountsPalette,
    card: ProjectCard,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
)
{
    val barColor = if (card.isOverTarget) palette.error else palette.iconToneGreen
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = card.displayEmoji(), fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.project.name.value,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (card.transactionCount == 0)
                    {
                        stringResource(R.string.projects_transactions_none)
                    } else
                    {
                        pluralStringResource(R.plurals.projects_transactions, card.transactionCount, card.transactionCount)
                    },
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.projects_card_cost, formatEuroCents(card.netCents)),
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            card.progress.target?.let { target ->
                Text(
                    text = stringResource(R.string.projects_card_target, formatEuroCents(target.value)),
                    color = palette.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
        card.barFraction?.let { fraction ->
            // A track and a fill: the fill is the share of the target spent, full (and red) once it is passed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.badgeBackground),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(50))
                        .background(barColor),
                )
            }
        }
        when (val remaining = card.remaining)
        {
            is ProjectRemaining.Left -> Text(
                text = stringResource(R.string.projects_card_left, formatEuroCents(remaining.cents)),
                color = palette.textMuted,
                fontSize = 13.sp,
            )

            is ProjectRemaining.Over -> Text(
                text = stringResource(R.string.projects_card_over, formatEuroCents(remaining.cents)),
                color = palette.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            null                     -> Unit
        }
    }
}
