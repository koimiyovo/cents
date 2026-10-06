package com.kyovo.cents.ui.backup

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Against a store kept in memory ([InMemoryPreferences]): what is checked is the adapter (its key,
 * replacing, clearing), not DataStore's own file handling.
 */
class DataStoreAutomaticBackupSettingsTest
{
    private val settings = DataStoreAutomaticBackupSettings(InMemoryPreferences())

    @Test
    fun `no folder is chosen at first`() = runTest()
    {
        assertThat(settings.folder()).isNull()
    }

    @Test
    fun `the chosen folder is remembered`() = runTest()
    {
        settings.chooseFolder("content://tree/backups")

        assertThat(settings.folder()).isEqualTo("content://tree/backups")
        assertThat(settings.observeFolder().first()).isEqualTo("content://tree/backups")
    }

    @Test
    fun `choosing another folder replaces the first`() = runTest()
    {
        settings.chooseFolder("content://tree/one")

        settings.chooseFolder("content://tree/two")

        assertThat(settings.folder()).isEqualTo("content://tree/two")
    }

    @Test
    fun `clearing turns the backup off`() = runTest()
    {
        settings.chooseFolder("content://tree/backups")

        settings.clearFolder()

        assertThat(settings.folder()).isNull()
        assertThat(settings.observeFolder().first()).isNull()
    }
}
