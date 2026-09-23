package com.kyovo.cents.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.TransactionCategory
import com.kyovo.cents.domain.port.input.GetAccountBalanceUseCase
import com.kyovo.cents.domain.port.input.ListAccountsUseCase
import com.kyovo.cents.domain.port.input.ListTransactionsUseCase
import com.kyovo.cents.ui.common.IconTone
import com.kyovo.cents.ui.common.formatEuroCents
import com.kyovo.cents.ui.common.formatSignedEuroCents
import java.time.LocalTime

@Composable
fun AccountsScreen(
    listAccounts: ListAccountsUseCase,
    getAccountBalance: GetAccountBalanceUseCase,
    listTransactions: ListTransactionsUseCase,
    modifier: Modifier = Modifier,
)
{
    val palette = if (isSystemInDarkTheme()) DarkAccountsPalette else LightAccountsPalette
    var balancesVisible by rememberSaveable { mutableStateOf(true) }
    // Landscape gives this screen roughly a third of the vertical space portrait does, so the
    // hero card trims its own padding/spacing/type scale to still clear the bottom nav without
    // needing a scroll just to see the balance.
    val isCompact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val accounts = remember { listAccounts.list() }
    val balanceByAccountId = remember(accounts) {
        accounts.associate { it.id to (getAccountBalance.getBalance(it.id)?.value ?: 0L) }
    }
    val totalCents = remember(balanceByAccountId) { balanceByAccountId.values.sum() }
    val incomeCents = remember {
        listTransactions.list(category = TransactionCategory.INCOME).sumOf { it.amount.value }
    }
    val expenseCents = remember {
        listTransactions.list(category = TransactionCategory.EXPENSE).sumOf { it.amount.value }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = if (isCompact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 12.dp else 20.dp),
    ) {
        AccountsTopBar(palette)
        GreetingRow(palette)
        TotalBalanceCard(
            palette = palette,
            totalCents = totalCents,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            visible = balancesVisible,
            onToggleVisibility = { balancesVisible = !balancesVisible },
            compact = isCompact,
        )
        NewAccountButton(palette)
        AccountsSectionHeader(palette, count = accounts.size)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accounts.forEachIndexed { index, account ->
                AccountRow(
                    account = account,
                    balanceCents = balanceByAccountId[account.id] ?: 0L,
                    visible = balancesVisible,
                    tone = IconTone.entries[index % IconTone.entries.size],
                    palette = palette,
                )
            }
        }
        TipCard(palette)
    }
}

@Composable
private fun AccountsTopBar(palette: AccountsPalette)
{
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.accounts_kicker),
            color = palette.kicker,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.accounts_title),
            color = palette.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Bonjour before 6 PM, Bonsoir from then on — computed once per composition, not live-updating. */
internal fun greetingStringRes(now: LocalTime): Int
{
    val hour = now.hour
    return if (hour in 6..17) R.string.accounts_greeting_day else R.string.accounts_greeting_evening
}

@Composable
private fun GreetingRow(palette: AccountsPalette)
{
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(greetingStringRes(LocalTime.now())),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.accounts_greeting_subtitle),
                color = palette.textMuted,
                fontSize = 13.sp,
            )
        }
        Pill(
            text = stringResource(R.string.accounts_vault_badge),
            background = palette.badgeBackground,
            content = palette.badgeContent,
        )
    }
}

@Composable
private fun TotalBalanceCard(
    palette: AccountsPalette,
    totalCents: Long,
    incomeCents: Long,
    expenseCents: Long,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    compact: Boolean,
)
{
    val hidden = stringResource(R.string.accounts_hidden_balance)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.heroCardBackground)
            .padding(if (compact) 14.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.accounts_hero_label),
                color = palette.heroOnCardSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            EyeToggleIcon(
                visible = visible,
                tint = palette.heroOnCardSecondary,
                modifier = Modifier
                    .clickable(onClick = onToggleVisibility)
                    .padding(4.dp)
                    .size(20.dp),
            )
        }
        Text(
            text = if (visible) formatEuroCents(totalCents) else hidden,
            color = palette.heroOnCardPrimary,
            fontSize = if (compact) 24.sp else 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroStatPill(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.accounts_income_label),
                amount = if (visible) formatSignedEuroCents(incomeCents) else hidden,
                accent = palette.heroIncomeAccent,
                palette = palette,
                compact = compact,
            )
            HeroStatPill(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.accounts_expense_label),
                amount = if (visible) formatSignedEuroCents(-expenseCents) else hidden,
                accent = palette.heroExpenseAccent,
                palette = palette,
                compact = compact,
            )
        }
    }
}

/**
 * A simple hand-drawn eye glyph (almond outline + pupil), struck through when [visible] is false.
 * Drawn rather than using an emoji or a Material icon: there's no single reliable "eye with
 * slash" glyph across fonts, and this keeps the same custom-icon style as the coin mark.
 */
@Composable
private fun EyeToggleIcon(visible: Boolean, tint: Color, modifier: Modifier = Modifier)
{
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = w * 0.09f
        val eyeOutline = Path().apply {
            moveTo(0f, h / 2f)
            quadraticTo(w / 2f, -h * 0.2f, w, h / 2f)
            quadraticTo(w / 2f, h * 1.2f, 0f, h / 2f)
            close()
        }
        drawPath(
            eyeOutline,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawCircle(color = tint, radius = h * 0.16f, center = Offset(w / 2f, h / 2f))
        if (!visible)
        {
            drawLine(
                color = tint,
                start = Offset(w * 0.05f, h * 0.95f),
                end = Offset(w * 0.95f, h * 0.05f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    amount: String,
    accent: Color,
    palette: AccountsPalette,
    compact: Boolean,
    modifier: Modifier = Modifier,
)
{
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.heroPillBackground)
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        Text(text = label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = amount,
            color = palette.heroOnCardPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NewAccountButton(palette: AccountsPalette)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(palette.primaryButtonBackground)
            .border(1.dp, palette.primaryButtonBorder, RoundedCornerShape(50))
            // Account creation isn't wired up yet — this button is a visual placeholder for now.
            .clickable(onClick = {})
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.accounts_new_account_button),
            color = palette.primaryButtonContent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AccountsSectionHeader(palette: AccountsPalette, count: Int)
{
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.accounts_section_title),
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.badgeBackground)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(
                    text = count.toString(),
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Text(
            text = stringResource(R.string.accounts_manage_link),
            color = palette.tipIconTone,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            // Account management isn't built yet — this link is a visual placeholder for now.
            modifier = Modifier.clickable(onClick = {}),
        )
    }
}

private data class AccountExtras(
    val emoji: String,
    val tag: String?,
    val subtitle: String,
    val noteOverride: String? = null,
)

private val accountExtrasByName = mapOf(
    "Compte Courant" to AccountExtras(
        emoji = "🏦",
        tag = "BNP",
        subtitle = "Solde de référence",
    ),
    "Livret A (Sécurité)" to AccountExtras(
        emoji = "🐷",
        tag = "3,0 %",
        subtitle = "Taux d'intérêts 3,0 %",
        noteOverride = "+6,00 €/mois",
    ),
    "Portefeuille Espèces" to AccountExtras(
        emoji = "💵",
        tag = null,
        subtitle = "Dernier retrait hier",
        noteOverride = "Poche physique",
    ),
    "Enveloppe Projets" to AccountExtras(
        emoji = "🎯",
        tag = null,
        subtitle = "Vacances & Loisirs",
        noteOverride = "Objectif 500 €",
    ),
)
private val defaultAccountExtras = AccountExtras(emoji = "💳", tag = null, subtitle = "")

@Composable
private fun AccountRow(
    account: Account,
    balanceCents: Long,
    visible: Boolean,
    tone: IconTone,
    palette: AccountsPalette,
)
{
    val extras = accountExtrasByName[account.name.value] ?: defaultAccountExtras
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(toneBackground(tone, palette)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = extras.emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.name.value,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (extras.tag != null)
                {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.badgeBackground)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(text = extras.tag, color = palette.textSecondary, fontSize = 11.sp)
                    }
                }
            }
            Text(text = extras.subtitle, color = palette.textMuted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            val hidden = stringResource(R.string.accounts_hidden_balance)
            Text(
                text = if (visible) formatEuroCents(balanceCents) else hidden,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            val isActive = account.archivedAt == null
            val note = extras.noteOverride
                ?: if (isActive) stringResource(R.string.accounts_status_active) else stringResource(
                    R.string.accounts_status_archived
                )
            Text(
                text = note,
                color = if (extras.noteOverride == null && isActive) palette.statusActiveColor else palette.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TipCard(palette: AccountsPalette)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.tipBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.accounts_tip_title),
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.accounts_tip_body),
            color = palette.textMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color, modifier: Modifier = Modifier)
{
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun toneBackground(tone: IconTone, palette: AccountsPalette): Color = when (tone)
{
    IconTone.Green -> palette.iconToneGreen
    IconTone.Gold  -> palette.iconToneGold
    IconTone.Mint  -> palette.iconToneMint
}
