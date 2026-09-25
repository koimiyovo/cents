package com.kyovo.cents.infrastructure.persistence.room

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.kyovo.cents.domain.model.DefaultSubcategories
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork
import java.nio.ByteBuffer
import java.util.UUID

/**
 * The app's storage, built once: one Room database, and the three repositories and the unit of work
 * that all work on it. What it shows to the outside are the domain's ports only, never a Room type, so
 * the app module needs to know nothing about Room.
 */
class RoomPersistence private constructor(private val database: CentsDatabase)
{
    val accounts: AccountRepository = RoomAccountRepository(database.accountDao())
    val subcategories: SubcategoryRepository = RoomSubcategoryRepository(database.subcategoryDao())
    val transactions: TransactionRepository = RoomTransactionRepository(database.transactionDao())
    val unitOfWork: UnitOfWork = RoomUnitOfWork(database)

    fun close()
    {
        database.close()
    }

    companion object
    {
        const val DATABASE_NAME = "cents.db"

        /**
         * The database file of the app, in its private storage. Nothing is opened yet: Room opens (and
         * creates, the first time) the file at the first query, off the main thread.
         *
         * Room 3 needs a SQLite driver. This one is Android's own SQLite, the phone's: nothing more to
         * ship with the app. (The bundled driver would give the same SQLite version everywhere, at a few
         * megabytes more.) There is deliberately no "destructive migration" fallback: if the schema ever
         * changes, a migration has to be written — wiping the user's accounts is never the answer.
         */
        fun open(context: Context, name: String = DATABASE_NAME): RoomPersistence
        {
            val database = Room.databaseBuilder(context.applicationContext, CentsDatabase::class.java, name)
                .setDriver(AndroidSQLiteDriver())
                .addMigrations(*CentsMigrations.ALL.toTypedArray())
                .addCallback(DefaultSubcategoriesCallback())
                .build()
            return RoomPersistence(database)
        }

        /**
         * A database file at a given path, with a given driver, configured exactly like [open] (same
         * migrations, no destructive fallback). Room 3 builds a database without a `Context`, so this is
         * what the tests use to check what opening a file does, on the computer's JVM.
         */
        fun openFile(path: String, driver: SQLiteDriver): RoomPersistence
        {
            val database = Room.databaseBuilder<CentsDatabase>(path)
                .setDriver(driver)
                .addMigrations(*CentsMigrations.ALL.toTypedArray())
                .addCallback(DefaultSubcategoriesCallback())
                .build()
            return RoomPersistence(database)
        }

        /** A database that lives in memory only, for the tests (it starts with the common subcategories, like a new file). */
        fun inMemory(driver: SQLiteDriver): RoomPersistence
        {
            val database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
                .setDriver(driver)
                .addCallback(DefaultSubcategoriesCallback())
                .build()
            return RoomPersistence(database)
        }
    }
}

/**
 * Puts the common subcategories (see [DefaultSubcategories]) into the database when it is created, and
 * only then: [onCreate] runs once, right after the tables are made, when the file is new. From then on
 * they are the user's, who may rename or delete them, and none comes back at a later launch. (Seeding at
 * every start "if the table is empty" would bring back the ones a user deleted on purpose.)
 *
 * Room does not let this callback use the DAOs, so it writes the rows with plain SQL, from the same
 * entity conversion the repository uses. The tests read them back through the repository, which is what
 * keeps this SQL and the table in step.
 */
private class DefaultSubcategoriesCallback : RoomDatabase.Callback()
{
    override suspend fun onCreate(connection: SQLiteConnection)
    {
        connection.prepare("INSERT INTO subcategories (id, kind, name, emoji) VALUES (?, ?, ?, ?)").use { statement ->
            for (subcategory in DefaultSubcategories.ALL)
            {
                val row = subcategory.toEntity()
                statement.reset()
                statement.bindBlob(1, row.id.toBytes())
                statement.bindText(2, row.kind)
                statement.bindText(3, row.name)
                if (row.emoji == null) statement.bindNull(4) else statement.bindText(4, row.emoji)
                statement.step()
            }
        }
    }

    /** A UUID as Room stores it: 16 bytes, the most significant half first. */
    private fun UUID.toBytes(): ByteArray
    {
        return ByteBuffer.allocate(16).putLong(mostSignificantBits).putLong(leastSignificantBits).array()
    }
}
