package com.kyovo.cents.infrastructure.persistence.room

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.kyovo.cents.domain.model.RecordableTransactionCategory
import com.kyovo.cents.domain.model.Subcategory
import com.kyovo.cents.domain.model.SubcategoryEmoji
import com.kyovo.cents.domain.model.SubcategoryId
import com.kyovo.cents.domain.model.SubcategoryName
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

/** What only a database on disk can do: survive being closed. */
class RoomSubcategoryRepositoryTest
{
    @TempDir
    lateinit var folder: File

    private fun open(): CentsDatabase =
        Room.databaseBuilder<CentsDatabase>(File(folder, "cents.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()

    @Test
    fun `what was saved is still there when the database is closed and opened again`(): Unit = runBlocking()
    {
        // GIVEN subcategories saved, then the database closed (as when the app is killed)
        val groceries = Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555551")),
            RecordableTransactionCategory.EXPENSE,
            SubcategoryName("Alimentation"),
            SubcategoryEmoji("🛒"),
        )
        val salary = Subcategory(
            SubcategoryId(UUID.fromString("55555555-5555-5555-5555-555555555552")),
            RecordableTransactionCategory.INCOME,
            SubcategoryName("Salaire"),
            null,
        )
        val first = open()
        RoomSubcategoryRepository(first.subcategoryDao()).also { it.save(groceries); it.save(salary) }
        first.close()

        // WHEN the file is opened by a new database
        val second = open()
        val found = RoomSubcategoryRepository(second.subcategoryDao()).findAll()
        second.close()

        // THEN
        assertThat(found).containsExactly(groceries, salary)
    }
}
