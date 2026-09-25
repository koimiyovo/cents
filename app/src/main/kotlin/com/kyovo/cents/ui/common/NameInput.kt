package com.kyovo.cents.ui.common

// The name typed for an account or a subcategory. Same idea as [acceptsAmountInput]: the field can't
// hold something the domain would refuse, rather than accepting it and complaining afterwards.

/**
 * What a name field holds after an edit: the text as typed, cut at [maxLength] (the domain's limit,
 * `AccountName.MAX_LENGTH` / `SubcategoryName.MAX_LENGTH`). A long text pasted in is cut rather than
 * refused — keeping its beginning is what the user most likely wants — and the cut never leaves half
 * an emoji (a lone surrogate) at the end.
 */
internal fun limitNameInput(text: String, maxLength: Int): String
{
    val cut = text.take(maxLength)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
