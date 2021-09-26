package com.github.skgmn.lang

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
@OptIn(ObsoleteCoroutinesApi::class)
private class StateCombine<T, R>(
    private val sources: Array<out StateFlow<T>>,
    private val transform: (Array<T>) -> R
) : StateFlow<R> {
    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<R>) {
        val latestValues = Array<Any?>(sources.size) { sources[it].value }
        collector.emit(transform(latestValues as Array<T>))
        coroutineScope {
            val actor = actor<SourceEmission<T>> {
                for (emission in channel) {
                    latestValues[emission.index] = emission.value
                    collector.emit(transform(latestValues as Array<T>))
                }
            }
            for (i in sources.indices) {
                val source = sources[i]
                launch {
                    source.collect {
                        actor.send(SourceEmission(i, it))
                    }
                }
            }
        }
    }

    override val replayCache: List<R>
        get() = listOf(value)

    override val value: R
        get() {
            val values = Array<Any?>(sources.size) { sources[it].value }
            return transform(values as Array<T>)
        }

    private class SourceEmission<T>(
        val index: Int,
        val value: T
    )
}

fun <T, R> stateCombine(
    source1: StateFlow<T>,
    source2: StateFlow<T>,
    transform: (T, T) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2)) { values ->
        transform(values[0], values[1])
    }

fun <T, R> stateCombine(
    source1: StateFlow<T>,
    source2: StateFlow<T>,
    source3: StateFlow<T>,
    transform: (T, T, T) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3)) { values ->
        transform(values[0], values[1], values[2])
    }

fun <T, R> stateCombine(
    source1: StateFlow<T>,
    source2: StateFlow<T>,
    source3: StateFlow<T>,
    source4: StateFlow<T>,
    transform: (T, T, T, T) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4)) { values ->
        transform(values[0], values[1], values[2], values[3])
    }

fun <T, R> stateCombine(
    source1: StateFlow<T>,
    source2: StateFlow<T>,
    source3: StateFlow<T>,
    source4: StateFlow<T>,
    source5: StateFlow<T>,
    transform: (T, T, T, T, T) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4, source5)) { values ->
        transform(values[0], values[1], values[2], values[3], values[4])
    }

fun <T, R> stateCombine(vararg source: StateFlow<T>, transform: (Array<T>) -> R): StateFlow<R> =
    StateCombine(source, transform)