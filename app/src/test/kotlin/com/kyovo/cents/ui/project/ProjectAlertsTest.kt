package com.kyovo.cents.ui.project

import com.kyovo.cents.domain.model.BudgetAlertLevel
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

private val CLOSE = BudgetAlertLevel.CLOSE_TO_LIMIT
private val OVER = BudgetAlertLevel.OVER

/**
 * A project's alert is told when saving a transaction *brings it up* a band - from nothing to close to the
 * target, or to over it, or from close to over - never while it stays where it was, and never on the way down.
 * There is nothing to remember between two saves: the level before and the level after say it all.
 */
class ProjectAlertsTest
{
    @Test
    fun `reaching the close band from below is an alert`()
    {
        assertThat(newlyReachedLevel(before = null, after = CLOSE)).isEqualTo(CLOSE)
    }

    @Test
    fun `going over from below, or from the close band, is an alert`()
    {
        assertThat(newlyReachedLevel(before = null, after = OVER)).isEqualTo(OVER)
        assertThat(newlyReachedLevel(before = CLOSE, after = OVER)).isEqualTo(OVER)
    }

    @Test
    fun `staying in the same band is no alert`()
    {
        assertThat(newlyReachedLevel(before = CLOSE, after = CLOSE)).isNull()
        assertThat(newlyReachedLevel(before = OVER, after = OVER)).isNull()
        assertThat(newlyReachedLevel(before = null, after = null)).isNull()
    }

    @Test
    fun `going down is no alert`()
    {
        assertThat(newlyReachedLevel(before = OVER, after = CLOSE)).isNull()
        assertThat(newlyReachedLevel(before = OVER, after = null)).isNull()
        assertThat(newlyReachedLevel(before = CLOSE, after = null)).isNull()
    }

    // ------------------------------------------------------------------ what it says on screen

    private fun aProject(emoji: String?) =
        Project(ProjectId(UUID.randomUUID()), ProjectName("Voyage au Japon"), emoji?.let { Emoji(it) }, null)

    @Test
    fun `a notice names the project by its emoji and its name, and carries the level`()
    {
        val notice = projectAlertNotice(ProjectAlert(aProject("✈️"), OVER))

        assertThat(notice).isEqualTo(ProjectAlertNotice("✈️", "Voyage au Japon", OVER))
    }

    @Test
    fun `a project without an emoji shows the default one`()
    {
        val notice = projectAlertNotice(ProjectAlert(aProject(null), CLOSE))

        assertThat(notice.emoji).isEqualTo(DEFAULT_PROJECT_EMOJI)
        assertThat(notice.level).isEqualTo(CLOSE)
    }
}
