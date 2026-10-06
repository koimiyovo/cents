package com.kyovo.cents.infrastructure.persistence.room

import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The database row is its own type, apart from the domain's [Project]: plain values (the target in cents,
 * `null` when there is none), and two functions to convert between them.
 */
class ProjectEntityMapperTest
{
    private val uuid = UUID.fromString("66666666-6666-6666-6666-666666666661")

    private val japan = Project(ProjectId(uuid), ProjectName("Voyage au Japon"), Emoji("✈️"), Money(300_000))

    @Test
    fun `a project becomes a row of plain values`()
    {
        assertThat(japan.toEntity()).isEqualTo(ProjectEntity(uuid, "Voyage au Japon", "✈️", 300_000, 80))
    }

    @Test
    fun `a project without an emoji or a target has neither in its row`()
    {
        // WHEN
        val entity = japan.copy(emoji = null, target = null).toEntity()

        // THEN
        assertThat(entity.emoji).isNull()
        assertThat(entity.targetCents).isNull()
    }

    @Test
    fun `the alert threshold is stored as a whole percentage`()
    {
        assertThat(japan.copy(alertThreshold = AlertThreshold(60)).toEntity().alertPercent).isEqualTo(60)
    }

    @Test
    fun `a row becomes the project it came from, threshold included`()
    {
        val own = japan.copy(alertThreshold = AlertThreshold(65))

        assertThat(own.toEntity().toDomain()).isEqualTo(own)
        assertThat(own.toEntity().toDomain().alertThreshold).isEqualTo(AlertThreshold(65))
    }

    @Test
    fun `a row with an alert threshold outside 1 to 100 is refused`()
    {
        assertThatThrownBy { ProjectEntity(uuid, "Voyage", null, null, 0).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { ProjectEntity(uuid, "Voyage", null, null, 101).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a row becomes the project it came from`()
    {
        assertThat(japan.toEntity().toDomain()).isEqualTo(japan)
        assertThat(japan.copy(emoji = null, target = null).toEntity().toDomain())
            .isEqualTo(japan.copy(emoji = null, target = null))
    }

    // A row the domain would never have produced must not be turned into a project by guessing.
    @Test
    fun `a row with a target of zero is refused`()
    {
        assertThatThrownBy { ProjectEntity(uuid, "Voyage", null, 0, 80).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `a row with a blank name is refused`()
    {
        assertThatThrownBy { ProjectEntity(uuid, "   ", null, null, 80).toDomain() }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
