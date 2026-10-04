package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.FixedProjectIdGenerator
import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.aCreateProjectCommand
import com.kyovo.cents.application.fakes.aMoney
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.application.fakes.assertThatThrownBySuspending
import com.kyovo.cents.domain.exception.DuplicateProjectNameException
import com.kyovo.cents.domain.model.Emoji
import com.kyovo.cents.domain.model.AlertThreshold
import com.kyovo.cents.domain.model.ProjectName
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * A project is something the user names ("Voyage au Japon", "Travaux cuisine"), with an optional target.
 * Its name is unique among projects, however it is written: case, surrounding spaces and accents ignored.
 */
class CreateProjectServiceTest
{
    private val generatedId = aProjectId()
    private val existingId = aProjectId("11111111-1111-1111-1111-111111111111")
    private val repository = InMemoryProjectRepository()
    private val service = CreateProjectService(repository, FixedProjectIdGenerator(generatedId))

    @Test
    fun `creates a project with the given name and a generated id, and saves it`() = runTest()
    {
        // WHEN
        val result = service.create(aCreateProjectCommand())

        // THEN
        val expected = aProject(id = generatedId)
        assertThat(result).isEqualTo(expected)
        assertThat(repository.saved).containsExactly(expected)
    }

    @Test
    fun `keeps the target when one is given, and has none otherwise`() = runTest()
    {
        // WHEN
        val withTarget = service.create(aCreateProjectCommand(target = aMoney(300_000)))
        val without = service.create(aCreateProjectCommand(name = ProjectName("Travaux cuisine")))

        // THEN
        assertThat(withTarget.target).isEqualTo(aMoney(300_000))
        assertThat(without.target).isNull()
    }

    @Test
    fun `keeps the alert threshold when one is given, and uses the default otherwise`() = runTest()
    {
        // WHEN
        val own = service.create(aCreateProjectCommand(target = aMoney(100_000), alertThreshold = AlertThreshold(60)))
        val default = service.create(aCreateProjectCommand(name = ProjectName("Travaux cuisine")))

        // THEN
        assertThat(own.alertThreshold).isEqualTo(AlertThreshold(60))
        assertThat(default.alertThreshold).isEqualTo(AlertThreshold.DEFAULT)
    }

    @Test
    fun `keeps the emoji when one is given, and has none otherwise`() = runTest()
    {
        // GIVEN
        val plane = Emoji("✈️")

        // WHEN
        val withEmoji = service.create(aCreateProjectCommand(emoji = plane))
        val without = service.create(aCreateProjectCommand(name = ProjectName("Travaux cuisine")))

        // THEN
        assertThat(withEmoji.emoji).isEqualTo(plane)
        assertThat(without.emoji).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Voyage au Japon", "voyage AU japon", "  Voyage au Japon  ", "Voyagé au Japon"])
    fun `throws when a project already has that name, whatever the case, spaces or accents`(name: String) =
        runTest()
        {
            // GIVEN
            repository.save(aProject(id = existingId))

            // WHEN / THEN
            assertThatThrownBySuspending {
                service.create(
                    aCreateProjectCommand(
                        name = ProjectName(
                            name
                        )
                    )
                )
            }
                .isInstanceOf(DuplicateProjectNameException::class.java)
        }

    @Test
    fun `saves nothing when the name is already taken`() = runTest()
    {
        // GIVEN
        val existing = aProject(id = existingId)
        repository.save(existing)

        // WHEN
        assertThatThrownBySuspending { service.create(aCreateProjectCommand()) }
            .isInstanceOf(DuplicateProjectNameException::class.java)

        // THEN
        assertThat(repository.saved).containsExactly(existing)
    }

    @Test
    fun `accepts a name that no other project has`() = runTest()
    {
        // GIVEN
        repository.save(aProject(id = existingId, name = ProjectName("Travaux cuisine")))

        // WHEN
        val result = service.create(aCreateProjectCommand())

        // THEN
        assertThat(repository.saved).hasSize(2)
        assertThat(result.name).isEqualTo(ProjectName("Voyage au Japon"))
    }
}
