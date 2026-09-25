package com.kyovo.cents.ui.home

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DeleteChoicesTest
{
    @Test
    fun `an account without transactions is deleted plainly, and archiving is not proposed`()
    {
        // WHEN
        val choices = deleteChoices(transactionCount = 0, alreadyArchived = false)

        // THEN there is no history to lose, so no gentler alternative to offer either
        assertThat(choices.deletesTransactions).isFalse()
        assertThat(choices.offersArchive).isFalse()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 250])
    fun `an account with transactions deletes them with it, and archiving is proposed instead`(count: Int)
    {
        // WHEN
        val choices = deleteChoices(transactionCount = count, alreadyArchived = false)

        // THEN
        assertThat(choices.deletesTransactions).isTrue()
        assertThat(choices.offersArchive).isTrue()
    }

    @Test
    fun `an archived account with transactions still deletes them, but archiving is no alternative`()
    {
        // WHEN
        val choices = deleteChoices(transactionCount = 12, alreadyArchived = true)

        // THEN
        assertThat(choices.deletesTransactions).isTrue()
        assertThat(choices.offersArchive).isFalse()
    }

    @Test
    fun `an archived account without transactions is deleted plainly`()
    {
        // WHEN
        val choices = deleteChoices(transactionCount = 0, alreadyArchived = true)

        // THEN
        assertThat(choices.deletesTransactions).isFalse()
        assertThat(choices.offersArchive).isFalse()
    }
}
