@file:Suppress("UNCHECKED_CAST")

package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ObsoleteCoroutinesApi::class)
private class StateCombine<R>(
    private val sources: Array<out StateFlow<Any?>>,
    private val transform: (Array<Any?>) -> R
) : StateFlow<R> {
    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<R>) {
        val latestValues = Array(sources.size) { sources[it].value }
        collector.emit(transform(latestValues))
        coroutineScope {
            val actor = actor<SourceEmission> {
                for (emission in channel) {
                    if (latestValues[emission.index] != emission.value) {
                        latestValues[emission.index] = emission.value
                        collector.emit(transform(latestValues))
                    }
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
            val values = Array(sources.size) { sources[it].value }
            return transform(values)
        }

    private class SourceEmission(
        val index: Int,
        val value: Any?
    )
}

fun <T1, T2, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    transform: (T1, T2) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2)) { values ->
        transform(values[0] as T1, values[1] as T2)
    }

fun <T1, T2, T3, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    transform: (T1, T2, T3) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3)) { values ->
        transform(values[0] as T1, values[1] as T2, values[2] as T3)
    }

fun <T1, T2, T3, T4, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    source4: StateFlow<T4>,
    transform: (T1, T2, T3, T4) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4)) { values ->
        transform(values[0] as T1, values[1] as T2, values[2] as T3, values[3] as T4)
    }

fun <T1, T2, T3, T4, T5, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    source4: StateFlow<T4>,
    source5: StateFlow<T5>,
    transform: (T1, T2, T3, T4, T5) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4, source5)) { values ->
        transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5
        )
    }

fun <T1, T2, T3, T4, T5, T6, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    source4: StateFlow<T4>,
    source5: StateFlow<T5>,
    source6: StateFlow<T6>,
    transform: (T1, T2, T3, T4, T5, T6) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4, source5, source6)) { values ->
        transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5,
            values[5] as T6
        )
    }

fun <T1, T2, T3, T4, T5, T6, T7, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    source4: StateFlow<T4>,
    source5: StateFlow<T5>,
    source6: StateFlow<T6>,
    source7: StateFlow<T7>,
    transform: (T1, T2, T3, T4, T5, T6, T7) -> R
): StateFlow<R> =
    StateCombine(arrayOf(source1, source2, source3, source4, source5, source6, source7)) { values ->
        transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5,
            values[5] as T6,
            values[5] as T7
        )
    }

fun <T1, T2, T3, T4, T5, T6, T7, T8, R> stateCombine(
    source1: StateFlow<T1>,
    source2: StateFlow<T2>,
    source3: StateFlow<T3>,
    source4: StateFlow<T4>,
    source5: StateFlow<T5>,
    source6: StateFlow<T6>,
    source7: StateFlow<T7>,
    source8: StateFlow<T8>,
    transform: (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): StateFlow<R> =
    StateCombine(
        arrayOf(
            source1,
            source2,
            source3,
            source4,
            source5,
            source6,
            source7,
            source8
        )
    ) { values ->
        transform(
            values[0] as T1,
            values[1] as T2,
            values[2] as T3,
            values[3] as T4,
            values[4] as T5,
            values[5] as T6,
            values[5] as T7,
            values[5] as T8
        )
    }

fun <T, R> stateCombine(vararg source: StateFlow<T>, transform: (Array<T>) -> R): StateFlow<R> =
    StateCombine(source, transform as (Array<Any?>) -> R)