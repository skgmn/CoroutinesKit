package com.github.skgmn.coroutineutils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.internal.FusibleFlow
import kotlin.coroutines.CoroutineContext

fun <T> Flow<T>.stateRefCount(initialValue: T): StateFlow<T> {
    return RefCountStateFlow(this, initialValue)
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class, DelicateCoroutinesApi::class)
private class RefCountStateFlow<T>(
    private val source: Flow<T>,
    private val initialValue: T
) : StateFlow<T>, FusibleFlow<T> {
    private val lock = Any()
    private var collectorState: CollectorState<T>? = null

    override suspend fun collect(collector: FlowCollector<T>) {
        val currentState = synchronized(lock) {
            collectorState?.also { ++it.refCount }
                ?: CollectorState(source, initialValue).also { collectorState = it }
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
        get() = synchronized(lock) { collectorState?.replayCache ?: emptyList() }

    override val value: T
        get() = synchronized(lock) { collectorState?.value ?: initialValue }

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