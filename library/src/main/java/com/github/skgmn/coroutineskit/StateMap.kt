package com.github.skgmn.coroutineskit

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect

@Suppress("UNCHECKED_CAST")
private class StateMap<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {
    private val lock = Any()

    @Volatile
    private var collecting = false

    @Volatile
    private var sourceValue: Any? = Uninitialized

    @Volatile
    private var transformedValue: Any? = Uninitialized

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<R>) {
        synchronized(lock) {
            collecting = true
            sourceValue = source.value
            transformedValue = Uninitialized
        }
        try {
            source.collect { sourceValue ->
                val value = synchronized(lock) {
                    this.sourceValue = sourceValue
                    val transformedValue = transform(sourceValue)
                    if (this.transformedValue != transformedValue) {
                        transformedValue.also { this.transformedValue = it }
                    } else {
                        Uninitialized
                    }
                }
                if (value !== Uninitialized) {
                    collector.emit(value as R)
                }
            }
        } finally {
            synchronized(lock) {
                sourceValue = Uninitialized
                transformedValue = Uninitialized
                collecting = false
            }
        }
    }

    override val replayCache: List<R>
        get() = listOf(value)

    override val value: R
        get() {
            return synchronized(lock) {
                if (collecting) {
                    if (transformedValue === Uninitialized) {
                        transform(sourceValue as T).also { transformedValue = it }
                    } else {
                        transformedValue as R
                    }
                } else {
                    val value = source.value
                    if (transformedValue === Uninitialized || sourceValue != value) {
                        sourceValue = value
                        transform(value).also { transformedValue = it }
                    } else {
                        transformedValue as R
                    }
                }
            }
        }
}

fun <T, R> StateFlow<T>.stateMap(transform: (T) -> R): StateFlow<R> =
    StateMap(this, transform)