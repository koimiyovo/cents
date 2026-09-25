package com.kyovo.cents

import android.app.Application

/**
 * Owns the [AppContainer] for the whole process. An Activity is destroyed and recreated on every
 * rotation or light/dark switch, so a container held by the Activity would re-seed fresh random
 * ids each time — and any id saved in the UI state (e.g. the opened account) would stop matching.
 * The Application object lives as long as the process does.
 */
class CentsApplication : Application() {
    val appContainer = AppContainer()
}
