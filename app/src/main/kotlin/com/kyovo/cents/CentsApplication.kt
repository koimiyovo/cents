package com.kyovo.cents

import android.app.Application
import android.content.pm.ApplicationInfo
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
 */
class CentsApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(RoomPersistence.open(this)) }

    // Work that belongs to the process rather than to a screen. A failure in one job must not cancel the others.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Demo data in debug builds only, and only into an empty database. A release build starts empty.
        // The seeding is a suspend function: it runs off the main thread, and the screens, which observe
        // the database, fill in by themselves when it is done.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            applicationScope.launch { appContainer.seedDemoDataIfEmpty() }
        }
    }
}
