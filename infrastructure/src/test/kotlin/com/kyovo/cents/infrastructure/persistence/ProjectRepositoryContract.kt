package com.kyovo.cents.infrastructure.persistence

import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.Money
import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.model.ProjectId
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.port.output.ProjectRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * What every [ProjectRepository] must do, whatever it stores the projects in: the port's specification,
 * written as tests. An adapter has a small test class extending this one and saying how to build itself
 * (today Room's, `RoomProjectRepositoryContractTest`).
 *
 * Like the other contracts, the tests run in real time (a database answers on its own threads) and the
 * observation tests wait until the wanted list shows up instead of counting emissions.
 */
abstract class ProjectRepositoryContract
{
    /** A fresh, empty repository. Called before each test. */
    protected abstract fun createRepository(): ProjectRepository

    protected lateinit var repository: ProjectRepository

    @BeforeEach
    fun createTheRepository()
    {
        repository = createRepository()
    }

    private val japan = aProject(1, "Voyage au Japon", "✈️", 300_000)
    private val kitchen = aProject(2, "Travaux cuisine", null, null)
    private val garden = aProject(3, "Jardin", "🌳", 85_050)

    private fun aProject(suffix: Int, name: String, emoji: String?, targetCents: Long?) =
        Project(
            ProjectId(UUID.fromString("66666666-6666-6666-6666-66666666666$suffix")),
            ProjectName(name),
            emoji?.let { Emoji(it) },
            targetCents?.let { Money(it) },
        )

    // ------------------------------------------------------------------ reading what was saved

    @Test
    fun `finds a project that has been saved, with its emoji and its target`() = realTime()
    {
        // GIVEN
        repository.save(japan)

        // WHEN / THEN
        assertThat(repository.findById(japan.id)).isEqualTo(japan)
    }

    @Test
    fun `keeps the alert threshold of each project, the default included`() = realTime()
    {
        // GIVEN
        val own = aProject(4, "Seuil 60", null, 100_000).copy(alertThreshold = AlertThreshold(60))
        val strict = aProject(5, "Seuil 100", null, 100_000).copy(alertThreshold = AlertThreshold(100))

        // WHEN
        repository.save(own)
        repository.save(strict)
        repository.save(japan)

        // THEN
        assertThat(repository.findById(own.id)?.alertThreshold).isEqualTo(AlertThreshold(60))
        assertThat(repository.findById(strict.id)?.alertThreshold).isEqualTo(AlertThreshold(100))
        assertThat(repository.findById(japan.id)?.alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
    }

    @Test
    fun `saving a project again replaces its alert threshold`() = realTime()
    {
        // GIVEN
        repository.save(japan)

        // WHEN
        repository.save(japan.copy(alertThreshold = AlertThreshold(50)))

        // THEN
        assertThat(repository.findById(japan.id)?.alertThreshold).isEqualTo(AlertThreshold(50))
    }

    @Test
    fun `finds nothing for an unknown id`() = realTime()
    {
        assertThat(repository.findById(japan.id)).isNull()
    }

    @Test
    fun `lists nothing when nothing was saved`() = realTime()
    {
        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `lists the projects in the order they were first saved`() = realTime()
    {
        // GIVEN
        repository.save(kitchen)
        repository.save(japan)
        repository.save(garden)

        // WHEN / THEN
        assertThat(repository.findAll()).containsExactly(kitchen, japan, garden)
    }

    @Test
    fun `a project without an emoji and without a target comes back as it went`() = realTime()
    {
        // GIVEN
        repository.save(kitchen)

        // WHEN
        val found = repository.findById(kitchen.id)

        // THEN
        assertThat(found?.emoji).isNull()
        assertThat(found?.target).isNull()
    }

    @Test
    fun `keeps the target to the cent, down to one and up to a very large amount`() = realTime()
    {
        // GIVEN
        val cheap = aProject(4, "Un centime", null, 1)
        val huge = aProject(5, "Un palais", null, 9_000_000_000_000L)

        // WHEN
        repository.save(cheap)
        repository.save(huge)

        // THEN
        assertThat(repository.findById(cheap.id)?.target).isEqualTo(Money(1))
        assertThat(repository.findById(huge.id)?.target).isEqualTo(Money(9_000_000_000_000L))
    }

    // ------------------------------------------------------------------ saving again

    @Test
    fun `saving an existing project replaces it where it stands`() = realTime()
    {
        // GIVEN
        repository.save(japan)
        repository.save(kitchen)
        repository.save(garden)

        // WHEN it is renamed, given another emoji and another target
        val renamed = japan.copy(name = ProjectName("Japon 2027"), emoji = Emoji("🗾"), target = Money(450_000))
        repository.save(renamed)

        // THEN it did not move, and nothing was duplicated
        assertThat(repository.findAll()).containsExactly(renamed, kitchen, garden)
        assertThat(repository.findById(japan.id)).isEqualTo(renamed)
    }

    @Test
    fun `an emoji and a target can be taken away`() = realTime()
    {
        // GIVEN
        repository.save(japan)

        // WHEN
        repository.save(japan.copy(emoji = null, target = null))

        // THEN
        val found = repository.findById(japan.id)
        assertThat(found?.emoji).isNull()
        assertThat(found?.target).isNull()
    }

    // The store must not judge the names: whether two names clash is a rule of the services.
    @Test
    fun `stores two projects with the same name`() = realTime()
    {
        // GIVEN
        val first = aProject(4, "Voyage", null, null)
        val second = aProject(5, "Voyage", null, null)

        // WHEN
        repository.save(first)
        repository.save(second)

        // THEN
        assertThat(repository.findAll()).containsExactly(first, second)
    }

    @Test
    fun `keeps names with quotes, accents and multi-part emojis intact`() = realTime()
    {
        // GIVEN
        val tricky = aProject(4, "L'été d'Élise \"bio\"; DROP TABLE x;--", "👨‍👩‍👧‍👦", null)

        // WHEN
        repository.save(tricky)

        // THEN
        assertThat(repository.findById(tricky.id)).isEqualTo(tricky)
    }

    @Test
    fun `keeps a name of the longest length`() = realTime()
    {
        // GIVEN
        val longest = aProject(4, "x".repeat(ProjectName.MAX_LENGTH), null, null)

        // WHEN
        repository.save(longest)

        // THEN
        assertThat(repository.findById(longest.id)).isEqualTo(longest)
    }

    // ------------------------------------------------------------------ deleting

    @Test
    fun `deletes a project and leaves the others`() = realTime()
    {
        // GIVEN
        repository.save(japan)
        repository.save(kitchen)

        // WHEN
        repository.deleteById(japan.id)

        // THEN
        assertThat(repository.findAll()).containsExactly(kitchen)
        assertThat(repository.findById(japan.id)).isNull()
    }

    @Test
    fun `is silent about deleting an unknown project`() = realTime()
    {
        // GIVEN
        repository.save(kitchen)

        // WHEN
        repository.deleteById(japan.id)

        // THEN
        assertThat(repository.findAll()).containsExactly(kitchen)
    }

    // ------------------------------------------------------------------ observing

    @Test
    fun `an observer first gets what is stored, in order`() = realTime()
    {
        // GIVEN
        repository.save(kitchen)
        repository.save(japan)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(kitchen, japan)
    }

    @Test
    fun `an observer of an empty repository gets an empty list`() = realTime()
    {
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `an observer collecting late still gets the current state`() = realTime()
    {
        // GIVEN changes made before anyone observes
        repository.save(japan)
        repository.save(kitchen)
        repository.deleteById(japan.id)

        // WHEN / THEN
        assertThat(repository.observeAll().first()).containsExactly(kitchen)
    }

    @Test
    fun `an observer follows the repository as projects are added, changed and deleted`() = realTime()
    {
        // GIVEN a screen collecting the projects
        val latest = repository.observeAll().stateIn(this, SharingStarted.Eagerly, null)
        latest.awaitMatching { it != null && it.isEmpty() }

        // WHEN / THEN each change shows up, in stored order
        repository.save(japan)
        latest.awaitMatching { it == listOf(japan) }

        repository.save(kitchen)
        latest.awaitMatching { it == listOf(japan, kitchen) }

        val retargeted = japan.copy(target = Money(500_000))
        repository.save(retargeted)
        latest.awaitMatching { it == listOf(retargeted, kitchen) }

        repository.deleteById(retargeted.id)
        latest.awaitMatching { it == listOf(kitchen) }

        coroutineContext.cancelChildren()
    }
}
