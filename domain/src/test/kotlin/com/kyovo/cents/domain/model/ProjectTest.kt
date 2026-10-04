package com.kyovo.cents.domain.model

import com.kyovo.cents.domain.exception.InvalidProjectTargetException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * A project groups transactions across subcategories (a trip: transport, restaurants, lodging). It may
 * have a target - the amount it is meant to cost overall, not per month - and may have none.
 */
class ProjectTest
{
    private val id = ProjectId(UUID.fromString("66666666-6666-6666-6666-666666666666"))
    private val name = ProjectName("Voyage au Japon")

    @Test
    fun `a project can have no target`()
    {
        assertThat(Project(id, name, null, null).target).isNull()
    }

    @Test
    fun `a project can have a target`()
    {
        assertThat(Project(id, name, null, Money(300_000)).target).isEqualTo(Money(300_000))
    }

    @Test
    fun `a project can have an emoji, and none`()
    {
        val plane = Emoji("✈️")

        assertThat(Project(id, name, plane, null).emoji).isEqualTo(plane)
        assertThat(Project(id, name, null, null).emoji).isNull()
    }

    @Test
    fun `a project starts with the default alert threshold, and can have its own`()
    {
        assertThat(Project(id, name, null, null).alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
        assertThat(Project(id, name, null, Money(100_000), AlertThreshold(60)).alertThreshold)
            .isEqualTo(AlertThreshold(60))
    }

    // A target of zero would be "over" at the first cent: it means nothing, as a budget limit of zero does.
    @Test
    fun `refuses a target of zero`()
    {
        assertThatThrownBy { Project(id, name, null, Money(0)) }
            .isInstanceOf(InvalidProjectTargetException::class.java)
    }
}
