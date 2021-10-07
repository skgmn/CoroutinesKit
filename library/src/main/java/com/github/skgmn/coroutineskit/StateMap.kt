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
    private var sourceValue: Any? = InvalidValue

    @Volatile
    private var transformedValue: Any? = InvalidValue

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<R>) {
        synchronized(lock) {
            collecting = true
            sourceValue = InvalidValue
            transformedValue = InvalidValue
        }
        try {
            source.collect { sourceValue ->
                val value = synchronized(lock) {
                    this.sourceValue = sourceValue
                    val transformedValue = transform(sourceValue)
                    if (this.transformedValue != transformedValue) {
                        transformedValue.also { this.transformedValue = it }
                    } else {
                        InvalidValue
                    }
                }
                if (value !== InvalidValue) {
                    collector.emit(value as R)
                }
            }
        } finally {
            synchronized(lock) {
                sourceValue = InvalidValue
                transformedValue = InvalidValue
                collecting = false
            }
        }
    }

    override val replayCache: List<R>
        get() = listOf(value)

    override val value: R
        get() {
            return synchronized(lock) {
                val value = source.value
                if (transformedValue === InvalidValue || sourceValue != value) {
                    if (collecting) {
                        transform(value)
                    } else {
                        sourceValue = value
                        transform(value).also { transformedValue = it }
                    }
                } else {
                    transformedValue as R
                }
            }
        }
}

fun <T, R> StateFlow<T>.stateMap(transform: (T) -> R): StateFlow<R> =
    StateMap(this, transform)