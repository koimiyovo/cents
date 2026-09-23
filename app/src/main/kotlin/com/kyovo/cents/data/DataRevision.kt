package com.kyovo.cents.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A counter bumped after every write. The screens read their data once through synchronous use
 * cases, so they collect [value] and re-read whenever it changes. It stands in for reactive queries
 * while storage is a plain in-memory list: with Room, a DAO returns a Flow that re-emits by itself
 * and this class goes away.
 */
class DataRevision
{
    private val _value = MutableStateFlow(0)
    val value: StateFlow<Int> = _value.asStateFlow()

    fun bump()
    {
        _value.update { it + 1 }
    }
}
