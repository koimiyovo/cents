package com.kyovo.cents.infrastructure.persistence.room

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.kyovo.cents.domain.port.output.AccountRepository
import com.kyovo.cents.domain.port.output.SubcategoryRepository
import com.kyovo.cents.domain.port.output.TransactionRepository
import com.kyovo.cents.domain.port.output.UnitOfWork

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
                .build()
            return RoomPersistence(database)
        }

        /** A database that lives in memory only, for the tests. */
        fun inMemory(driver: SQLiteDriver): RoomPersistence
        {
            val database = Room.inMemoryDatabaseBuilder<CentsDatabase>()
                .setDriver(driver)
                .build()
            return RoomPersistence(database)
        }
    }
}
