package com.kyovo.cents.domain.model

import java.util.UUID

/**
 * The subcategories the app is delivered with: common ones, so that a new user can categorise a first
 * transaction without creating anything. They are put into the database once, when it is created; from
 * then on they are ordinary subcategories that the user can rename or delete.
 *
 * Each id is derived from a fixed key (see [idOf]) rather than drawn at random: a future version of
 * the app may need to point at one of these, to add a new common subcategory next to them for instance.
 */
object DefaultSubcategories
{
    /** The id of the default subcategory with this key: always the same for the same key. */
    fun idOf(key: String): SubcategoryId
    {
        return SubcategoryId(UUID.nameUUIDFromBytes("cents:subcategory:$key".toByteArray(Charsets.UTF_8)))
    }

    private fun expense(key: String, name: String, emoji: String) =
        Subcategory(idOf("expense:$key"), RecordableTransactionCategory.EXPENSE, SubcategoryName(name), SubcategoryEmoji(emoji))

    private fun income(key: String, name: String, emoji: String) =
        Subcategory(idOf("income:$key"), RecordableTransactionCategory.INCOME, SubcategoryName(name), SubcategoryEmoji(emoji))

    // The emojis are written as escapes so that the source does not depend on a file encoding.
    val ALL: List<Subcategory> = listOf(
        expense("groceries", "Alimentation", "🛒"),
        expense("housing", "Logement", "🏠"),
        expense("transport", "Transport", "🚗"),
        expense("health", "Santé", "💊"),
        expense("leisure", "Loisirs", "🎬"),
        expense("restaurants", "Restaurants", "🍽️"),
        expense("clothing", "Vêtements", "👕"),
        expense("subscriptions", "Abonnements", "📱"),
        expense("education", "Éducation", "🎓"),
        expense("travel", "Voyages", "✈️"),
        expense("gifts", "Cadeaux", "🎁"),
        expense("taxes", "Impôts et taxes", "🧾"),
        expense("insurance", "Assurances", "🛡️"),
        expense("other", "Autre", "📦"),

        income("salary", "Salaire", "💰"),
        income("refund", "Remboursement", "💸"),
        income("side-income", "Revenus complémentaires", "💼"),
        income("investments", "Intérêts et placements", "📈"),
        income("benefits", "Aides et allocations", "👪"),
        income("gift", "Cadeau", "🎁"),
        income("other", "Autre", "📦"),
    )
}
