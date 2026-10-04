package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.anUpdateProjectCommand
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.exception.ProjectNotFoundException
import com.kyovo.cents.domain.model.ProjectName
import com.kyovo.cents.domain.model.Emoji
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * An update carries the whole new state, like the other update commands: the emoji and the target are
 * required-nullable, so passing null removes them (a default would let a caller erase them by accident).
 */
class UpdateProjectServiceTest
{
    private val id = aProjectId()
    private val otherId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val repository = InMemoryProjectRepository()
    private val service = UpdateProjectService(repository)

    @Test
    fun `renames the project and changes its target`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = id, name = ProjectName("Japon"), target = aMoney(100_000)))

        // WHEN
        val result = service.update(
            anUpdateProjectCommand(id = id, name = ProjectName("Voyage au Japon"), target = aMoney(300_000))
        )

        // THEN
        val expected = aProject(id = id, name = ProjectName("Voyage au Japon"), target = aMoney(300_000))
        assertThat(result).isEqualTo(expected)
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `changes the emoji, and a null emoji removes it`() = runTest()
    {
        // GIVEN
        val plane = Emoji("✈️")
        repository.save(aProject(id = id, emoji = Emoji("🏠")))

        // WHEN / THEN
        assertThat(service.update(anUpdateProjectCommand(id = id, emoji = plane)).emoji).isEqualTo(plane)
        assertThat(service.update(anUpdateProjectCommand(id = id, emoji = null)).emoji).isNull()
    }

    @Test
    fun `a null target removes the target`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = id, target = aMoney(100_000)))

        // WHEN
        val result = service.update(anUpdateProjectCommand(id = id, target = null))

        // THEN
        assertThat(result.target).isNull()
    }

    @Test
    fun `keeps the project where it stands among the others`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = id))
        repository.save(aProject(id = otherId, name = ProjectName("Travaux cuisine")))

        // WHEN
        service.update(anUpdateProjectCommand(id = id, name = ProjectName("Japon 2027")))

        // THEN
        assertThat(repository.saved.map { it.id }).containsExactly(id, otherId)
    }

    @Test
    fun `throws when no project matches the given id`() = runTest()
    {
        assertThatThrownBySuspending { service.update(anUpdateProjectCommand(id = id)) }
            .isInstanceOf(ProjectNotFoundException::class.java)
    }

    @Test
    fun `throws when another project already has the name, and changes nothing`() = runTest()
    {
        // GIVEN
        val project = aProject(id = id, name = ProjectName("Japon"))
        val other = aProject(id = otherId, name = ProjectName("Travaux cuisine"))
        repository.save(project)
        repository.save(other)

        // WHEN / THEN
        assertThatThrownBySuspending {
            service.update(anUpdateProjectCommand(id = id, name = ProjectName("travaux CUISINE")))
        }.isInstanceOf(DuplicateProjectNameException::class.java)
        assertThat(repository.saved).containsExactly(project, other)
    }

    // The project is not "another" project to itself: changing the case or an accent of its own name is fine.
    @Test
    fun `accepts a new spelling of its own name`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = id, name = ProjectName("Ete 2027")))

        // WHEN
        val result = service.update(anUpdateProjectCommand(id = id, name = ProjectName("Été 2027")))

        // THEN
        assertThat(result.name).isEqualTo(ProjectName("Été 2027"))
    }

    @Test
    fun `accepts the name of a project that was deleted`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = id, name = ProjectName("Japon")))
        repository.save(aProject(id = otherId, name = ProjectName("Travaux cuisine")))
        repository.deleteById(otherId)

        // WHEN
        val result = service.update(anUpdateProjectCommand(id = id, name = ProjectName("Travaux cuisine")))

        // THEN
        assertThat(result.name).isEqualTo(ProjectName("Travaux cuisine"))
    }
}
