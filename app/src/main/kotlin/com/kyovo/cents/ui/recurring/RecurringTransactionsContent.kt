package com.kyovo.cents.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.Account
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.port.input.ListRecurringTransactionsUseCase
import com.kyovo.cents.ui.common.formatSignedMoney
import com.kyovo.cents.ui.home.AccountsPalette

/**
 * The "Récurrentes" half of the Transactions tab, where the user manages their recurring transactions (rent,
 * subscriptions...): one row per rule with its amount, account and pace ("Tous les mois"...). Touching a row
 * opens its edit form, where it can also be deleted. [accounts] must include the archived ones — a rule on a
 * closed account is still listed (only generation itself skips it).
 */
@Composable
fun RecurringTransactionsContent(
    palette: AccountsPalette,
    listRecurringTransactions: ListRecurringTransactionsUseCase,
    accounts: List<Account>,
    subcategories: List<Subcategory>,
    onCreate: () -> Unit,
    onEdit: (RecurringTransactionRow) -> Unit,
)
{
    val recurringTransactions by remember { listRecurringTransactions.observe() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val rows = remember(recurringTransactions, accounts, subcategories) {
        recurringTransactionRows(recurringTransactions, accounts, subcategories)
    }

    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = stringResource(R.string.recurring_transactions_intro),
            color = palette.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        val newDescription = stringResource(R.string.recurring_transactions_new)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .clickable(onClick = onCreate)
                .semantics { contentDescription = newDescription },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", color = palette.kicker, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface),
    ) {
        rows.forEachIndexed { index, row ->
            RecurringTransactionRowItem(palette, row, onClick = { onEdit(row) })
            if (index != rows.lastIndex)
            {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
            }
        }
        if (rows.isEmpty())
        {
            Text(
                text = stringResource(R.string.recurring_transactions_empty),
                color = palette.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun RecurringTransactionRowItem(palette: AccountsPalette, row: RecurringTransactionRow, onClick: () -> Unit)
{
    val editLabel = stringResource(R.string.recurring_transactions_edit_action)
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
            Text(text = "🔁", fontSize = 18.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.recurringTransaction.title.value,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            val pace = recurrenceSummary(row.recurringTransaction.frequency, row.recurringTransaction.interval)
            Text(
                text = "${row.accountName} · $pace",
                color = palette.textMuted,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        // Which way the money goes, like the transactions list: minus in the expense colour, plus in the income one.
        Text(
            text = formatSignedMoney(row.signedAmountCents, row.currency),
            color = if (row.signedAmountCents >= 0) palette.heroIncomeAccent else palette.heroExpenseAccent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
