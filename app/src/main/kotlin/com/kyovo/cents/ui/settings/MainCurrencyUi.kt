package com.kyovo.cents.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyovo.cents.R
import com.kyovo.cents.domain.model.AccountCurrency
import com.kyovo.cents.ui.home.AccountsPalette

/**
 * The currency the totals are shown in. A CompositionLocal rather than a parameter: every screen with a total
 * reads it, and it only changes when the user picks another one in the top bar.
 */
val LocalMainCurrency = staticCompositionLocalOf { AccountCurrency.EUR }

/** What the top bar's currency button calls when the user picks another currency. */
val LocalChangeMainCurrency = staticCompositionLocalOf<(AccountCurrency) -> Unit> { {} }

/** "€ EUR" — what the pill and the menu show for a currency. */
internal fun AccountCurrency.label(): String
{
    return "${value.getSymbol(java.util.Locale.FRANCE)} ${value.currencyCode}"
}

/** The main currency at the left of the settings gear: a tap unfolds the three choices. */
@Composable
internal fun MainCurrencyPill(palette: AccountsPalette)
{
    val current = LocalMainCurrency.current
    val change = LocalChangeMainCurrency.current
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.main_currency_description)
    Box {
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .clickable { open = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = current.value.currencyCode,
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AccountCurrency.SUPPORTED.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = currency.label(),
                            fontWeight = if (currency == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { open = false; change(currency) },
                )
            }
        }
    }
}

/**
 * Asked once, at the first launch (and to anyone updating from a version that had no choice): which currency the
 * totals are shown in. It can be changed later from the top bar.
 */
@Composable
fun MainCurrencyChoiceScreen(onChosen: (AccountCurrency) -> Unit)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.main_currency_choice_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = stringResource(R.string.main_currency_choice_body), fontSize = 15.sp)
        AccountCurrency.SUPPORTED.forEach { currency ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChosen(currency) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(text = currency.label(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
