package com.kyovo.cents.application.usecase

import com.kyovo.cents.application.fakes.InMemoryProjectRepository
import com.kyovo.cents.application.fakes.aProject
import com.kyovo.cents.application.fakes.aProjectId
import com.kyovo.cents.domain.model.ProjectName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The projects are *observed*, by name ascending whatever the stored order (case and accents do not
 * weigh: "Été" sits between "Divers" and "Voyage"), so a renamed or new one lands where it belongs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListProjectsServiceTest
{
    private val repository = InMemoryProjectRepository()
    private val service = ListProjectsService(repository)

    private fun aNamed(suffix: Int, name: String) =
        aProject(id = aProjectId("11111111-1111-1111-1111-11111111111$suffix"), name = ProjectName(name))

    @Test
    fun `emits an empty list when there is no project`() = runTest()
    {
        assertThat(service.observe().first()).isEmpty()
    }

    @Test
    fun `emits every project ordered by name, whatever the stored order`() = runTest()
    {
        // GIVEN
        val trip = aNamed(1, "Voyage au Japon")
        val works = aNamed(2, "Travaux cuisine")
        val summer = aNamed(3, "Été 2027")
        repository.save(trip)
        repository.save(works)
        repository.save(summer)

        // WHEN / THEN
        assertThat(service.observe().first()).containsExactly(summer, works, trip)
    }

    @Test
    fun `emits again, in place, when a project is created or renamed`() = runTest(UnconfinedTestDispatcher())
    {
        // GIVEN
        val emissions = mutableListOf<List<String>>()
        val job = launch { service.observe().collect { emissions += it.map { project -> project.name.value } } }

        // WHEN
        repository.save(aNamed(1, "Voyage au Japon"))
        repository.save(aNamed(2, "Travaux cuisine"))
        repository.save(aNamed(1, "Abri de jardin"))
        job.cancel()

        // THEN
        assertThat(emissions.last()).containsExactly("Abri de jardin", "Travaux cuisine")
    }
}
