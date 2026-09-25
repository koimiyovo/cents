package com.kyovo.cents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A view model launches its work in `viewModelScope`, which runs on `Dispatchers.Main` — a dispatcher
 * that doesn't exist in a plain JVM test. This replaces it with an unconfined test dispatcher: a
 * coroutine that never really waits (the fakes don't) runs to its end inside the call that launched it,
 * so a test can call `viewModel.submit()` and assert right after, as it always did.
 *
 * Use it with `@ExtendWith(MainDispatcherExtension::class)` on the test class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback
{
    override fun beforeEach(context: ExtensionContext)
    {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext)
    {
        Dispatchers.resetMain()
    }
}
