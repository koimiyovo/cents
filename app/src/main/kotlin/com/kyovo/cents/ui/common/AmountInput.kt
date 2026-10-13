package com.kyovo.cents.ui.common

// Amount typed by the user, shared by every form that asks for one (transaction, opening balance).
// [fractionDigits] is the number of decimals of the currency being typed: 2 for the euro and the dollar,
// 0 for the CFA franc (which has no separator at all).

private fun amountPattern(fractionDigits: Int) =
    if (fractionDigits == 0) Regex("""\d+""") else Regex("""\d+([.,]\d{0,$fractionDigits})?""")

/**
 * "12,50" / "12.50" / "12" / "12," → the amount in the currency's smallest unit (cents), or null when the
 * text isn't a positive amount with at most [fractionDigits] decimals. Parsed from the digits directly,
 * never through Double (binary rounding), and an extra decimal is refused rather than rounded away.
 * Zero is refused unless [allowZero]: `Money` allows it, but a zero-amount transaction is never what the
 * user meant, whereas a new account may well start empty.
 */
internal fun parseAmountToCents(text: String, allowZero: Boolean = false, fractionDigits: Int = 2): Long?
{
    val trimmed = text.trim()
    if (!amountPattern(fractionDigits).matches(trimmed)) return null

    val separatorIndex = trimmed.indexOfFirst { it == ',' || it == '.' }
    val wholePart = if (separatorIndex == -1) trimmed else trimmed.substring(0, separatorIndex)
    val fractionPart = if (separatorIndex == -1) "" else trimmed.substring(separatorIndex + 1)

    val whole = wholePart.toLongOrNull() ?: return null
    val fraction = if (fractionDigits == 0) 0L else fractionPart.padEnd(fractionDigits, '0').toLong()
    var factor = 1L
    repeat(fractionDigits) { factor *= 10 }
    val cents = try
    {
        Math.addExact(Math.multiplyExact(whole, factor), fraction)
    } catch (_: ArithmeticException)
    {
        return null
    }
    return cents.takeIf { allowZero || it > 0 }
}

private fun amountInputPattern(fractionDigits: Int) =
    if (fractionDigits == 0) Regex("""(\d{1,12})?""") else Regex("""(\d{1,9}([.,]\d{0,$fractionDigits})?)?""")

/**
 * Whether [text] is an acceptable state of the amount field while typing: digits, then optionally
 * one separator and up to [fractionDigits] decimals ("12", "12,", "12,5", "12,50"). The field ignores any
 * edit that would leave this shape, so letters, a second separator or an extra decimal can't be typed at
 * all. Nine integer digits at most (twelve for a currency without decimals) keeps the amount well inside a Long.
 */
internal fun acceptsAmountInput(text: String, fractionDigits: Int = 2): Boolean =
    amountInputPattern(fractionDigits).matches(text)

/**
 * The amount in the currency's smallest unit → the text the amount field would hold for it ("1250" → "12,50",
 * "5" → "0,05"): what an existing transaction's amount is pre-filled with when it is edited. Always exactly
 * [fractionDigits] decimals.
 */
internal fun formatCentsForInput(cents: Long, fractionDigits: Int = 2): String
{
    if (fractionDigits == 0) return cents.toString()
    var factor = 1L
    repeat(fractionDigits) { factor *= 10 }
    return "${cents / factor},${(cents % factor).toString().padStart(fractionDigits, '0')}"
}
