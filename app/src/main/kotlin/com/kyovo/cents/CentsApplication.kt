package com.kyovo.cents

import android.app.Application
import com.kyovo.cents.infrastructure.persistence.room.RoomPersistence

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
}
