package com.github.skgmn.coroutineskit

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.internal.FusibleFlow
import kotlin.coroutines.CoroutineContext

/**
 * Turns Flow into StateFlow. Unlike stateIn() it does not require any CoroutineScope. It subscribes
 * the upstream when the downstream is firstly collected, and it cancels the upstream when the
 * downstream is lastly completed so that it can be used like RxJava's replay(1).refCount().
 */
fun <T> Flow<T>.stateRefCount(onGetInitialValue : () -> T): StateFlow<T> {
    return RefCountStateFlow(this, onGetInitialValue)
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class, DelicateCoroutinesApi::class)
private class RefCountStateFlow<T>(
    private val source: Flow<T>,
    private val onGetInitialValue : () -> T
) : StateFlow<T>, FusibleFlow<T> {
    private val lock = Any()
    private var collectorState: CollectorState<T>? = null

    override suspend fun collect(collector: FlowCollector<T>) {
        val currentState = synchronized(lock) {
            collectorState?.also { ++it.refCount }
                ?: CollectorState(source, onGetInitialValue()).also { collectorState = it }
        }
        try {
            currentState.collect(collector)
        } finally {
            synchronized(lock) {
                if (--currentState.refCount == 0) {
                    collectorState = null
                    currentState.dispose()
                }
            }
        }
    }

    override val replayCache: List<T>
        get() = synchronized(lock) { collectorState?.replayCache ?: listOf(onGetInitialValue()) }

    override val value: T
        get() = synchronized(lock) {
            val state = collectorState
            if (state != null) {
                state.value
            } else {
                onGetInitialValue()
            }
        }

    override fun fuse(
        context: CoroutineContext,
        capacity: Int,
        onBufferOverflow: BufferOverflow
    ): Flow<T> {
        return this
    }

    private class CollectorState<T>(source: Flow<T>, initialValue: T) {
        private val stateFlow = MutableStateFlow<Any?>(initialValue)

        private val job = GlobalScope.launch(start = CoroutineStart.LAZY) {
            try {
                source.collect(stateFlow)
                stateFlow.value = TerminalState(stateFlow.value, null)
            } catch (e: CollectorDisposedException) {
                // disposed
            } catch (e: Throwable) {
                stateFlow.value = TerminalState(stateFlow.value, e)
            }
        }

        @Volatile
        var refCount = 1

        val replayCache: List<T>
            get() = listOf(value)

        val value: T
            get() = stateFlow.value.let {
                if (it is TerminalState<*>) {
                    it.lastValue as T
                } else {
                    it as T
                }
            }

        fun dispose() {
            job.cancel(CollectorDisposedException())
        }

        suspend fun collect(collector: FlowCollector<T>) {
            var emitted = false
            stateFlow
                .onSubscription { job.start() }
                .transformWhile {
                    if (it is TerminalState<*> && it.e == null) {
                        if (!emitted) {
                            emit(it.lastValue)
                            emitted = true
                        }
                        false
                    } else {
                        emit(it)
                        true
                    }
                }
                .collect {
                    if (it is TerminalState<*>) {
                        if (!emitted) {
                            collector.emit(it.lastValue as T)
                            emitted = true
                        }
                        throw checkNotNull(it.e)
                    } else {
                        collector.emit(it as T)
                        emitted = true
                    }
                }
        }
    }

    private class TerminalState<T>(
        val lastValue: T,
        val e: Throwable?
    )
}