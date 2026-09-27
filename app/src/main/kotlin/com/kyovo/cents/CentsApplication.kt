package com.kyovo.cents

import android.app.Application
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the [AppContainer] for the whole process. An Activity is destroyed and recreated on every
 * rotation or light/dark switch, so a container held by the Activity would be rebuilt each time. The
 * Application object lives as long as the process does.
 *
 * The container is built at the first use, not in the constructor: it needs the application context
 * for the database file, which does not exist yet while the Application object is being constructed.
 * The database is created (with the common subcategories in it) the first time it is opened.
 */
class CentsApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(RoomPersistence.open(this)) }

    override fun onCreate() {
        super.onCreate()

        // Catches every recurring expense up as soon as the app opens, off the main thread: safe to run
        // every time, since an occurrence already generated is never redone.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            appContainer.generateRecurringExpenses.generate()
        }
    }
}
