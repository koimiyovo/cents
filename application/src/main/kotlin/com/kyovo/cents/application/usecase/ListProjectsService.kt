package com.kyovo.cents.application.usecase

import com.kyovo.cents.domain.model.Project
import com.kyovo.cents.domain.port.input.ListProjectsUseCase
import com.kyovo.cents.domain.port.output.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale

class ListProjectsService(private val projectRepository: ProjectRepository) : ListProjectsUseCase
{
    // Ascending by name, the way a French reader expects (case ignored, an accented letter sorts with its
    // plain one); equal names keep the stored order.
    private val byName: Comparator<Project> = Collator.getInstance(Locale.FRENCH)
        .apply { strength = Collator.PRIMARY }
        .let { collator -> compareBy(collator) { it.name.value } }

    override fun observe(): Flow<List<Project>>
    {
        return projectRepository.observeAll().map { projects -> projects.sortedWith(byName) }
    }
}
