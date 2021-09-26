package com.github.skgmn.lang

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

private class StateMap<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R
): StateFlow<R> {
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<R>) {
        source.collect {
            collector.emit(transform(it))
        }
    }

    override val replayCache: List<R>
        get() = source.replayCache.map(transform)

    override val value: R
        get() = transform(source.value)
}

fun <T, R> StateFlow<T>.stateMap(transform: (T) -> R): StateFlow<R> =
    StateMap(this, transform)