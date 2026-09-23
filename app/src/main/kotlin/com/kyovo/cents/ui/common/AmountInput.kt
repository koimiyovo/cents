package com.kyovo.cents.ui.common

// Amount typed by the user, shared by every form that asks for one (transaction, opening balance).

private val AMOUNT_PATTERN = Regex("""\d+([.,]\d{0,2})?""")

/**
 * "12,50" / "12.50" / "12" / "12," → cents, or null when the text isn't a positive amount with at most
 * two decimals. Parsed from the digits directly, never through Double (binary rounding), and a
 * third decimal is refused rather than rounded away. Zero is refused unless [allowZero]: `Money`
 * allows it, but a zero-amount transaction is never what the user meant, whereas a new account
 * may well start empty.
 */
internal fun parseAmountToCents(text: String, allowZero: Boolean = false): Long?
{
    val trimmed = text.trim()
    if (!AMOUNT_PATTERN.matches(trimmed)) return null

    val separatorIndex = trimmed.indexOfFirst { it == ',' || it == '.' }
    val wholePart = if (separatorIndex == -1) trimmed else trimmed.substring(0, separatorIndex)
    val fractionPart = if (separatorIndex == -1) "" else trimmed.substring(separatorIndex + 1)

    val whole = wholePart.toLongOrNull() ?: return null
    val fraction = fractionPart.padEnd(2, '0').toLong()
    val cents = try
    {
        Math.addExact(Math.multiplyExact(whole, 100L), fraction)
    } catch (_: ArithmeticException)
    {
        return null
    }
    return cents.takeIf { allowZero || it > 0 }
}

private val AMOUNT_INPUT_PATTERN = Regex("""(\d{1,9}([.,]\d{0,2})?)?""")

/**
 * Whether [text] is an acceptable state of the amount field while typing: digits, then optionally
 * one separator and up to two decimals ("12", "12,", "12,5", "12,50"). The field ignores any edit
 * that would leave this shape, so letters, a second separator or a third decimal can't be typed at
 * all. Nine integer digits at most keeps the amount well inside a Long of cents.
 */
internal fun acceptsAmountInput(text: String): Boolean = AMOUNT_INPUT_PATTERN.matches(text)
