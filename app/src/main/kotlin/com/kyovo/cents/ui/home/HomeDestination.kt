package com.kyovo.cents.ui.home

/**
 * Where the user is, besides an opened account: on the tabs, in the settings, or one level below
 * them, on the screen managing the subcategories.
 */
internal enum class HomeDestination
{
    Tabs,
    Settings,
    Subcategories;

    /**
     * Where Back leads from here — one level up at a time — or null on the tabs, where Back is left to
     * the system (which leaves the app).
     */
    fun back(): HomeDestination? = when (this)
    {
        Tabs          -> null
        Settings      -> Tabs
        Subcategories -> Settings
    }
}
