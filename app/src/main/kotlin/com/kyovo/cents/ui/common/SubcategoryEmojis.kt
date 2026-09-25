package com.kyovo.cents.ui.common

/**
 * The emojis offered when creating a subcategory: everyday budget life, grouped the way people
 * think of it (food, transport, home, health and care, leisure, family and tech, money). A fixed
 * list rather than the full Unicode set — a category needs a recognisable icon, not 3,000 choices —
 * and every entry is a single, valid emoji for the domain (checked by a test).
 *
 * Which of them a given phone can actually draw is decided at display time (see [EmojiPickerField]):
 * recent emojis show up as empty boxes on old Android versions.
 */
internal val SUBCATEGORY_EMOJIS: List<String> = listOf(
    // food
    "🛒", "🍎", "🍞", "🍕", "☕", "🍷", "🥗", "🍔",
    // transport
    "🚗", "⛽", "🚌", "🚆", "✈️", "🚲", "🅿️", "🧳",
    // home
    "🏠", "💡", "🔧", "🛋️", "🧹", "🔥", "💧", "📶",
    // health and care
    "💊", "🏥", "🦷", "💇", "💄", "👕", "👟", "🧴",
    // leisure
    "🎬", "🎮", "🎵", "📚", "⚽", "🎁", "🏖️", "🎉",
    // family and tech
    "👶", "🐶", "🎓", "📱", "💻", "📦", "🧾", "🏦",
    // money and misc
    "💰", "💸", "💳", "📈", "⭐", "❤️",
)
