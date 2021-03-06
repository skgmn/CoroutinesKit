package com.github.skgmn.coroutineskit

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.internal.FusibleFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Turns Flow into SharedFlow. Unlike shareIn() it does not require any CoroutineScope.
 * It subscribes the upstream when the downstream is firstly collected, and it cancels the upstream
 * when the downstream is lastly completed so that it can be used like RxJava's publish().refCount()
 * or replay(n).refCount() where n equals to [replay].
 */
fun <T> Flow<T>.shareRefCount(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): SharedFlow<T> {
    return RefCountSharedFlow(
        this,
        replay,
        extraBufferCapacity,
        onBufferOverflow
    )
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class, DelicateCoroutinesApi::class)
private class RefCountSharedFlow<T>(
    private val source: Flow<T>,
    private val replay: Int,
    private val extraBufferCapacity: Int,
    private val onBufferOverflow: BufferOverflow
) : SharedFlow<T>, FusibleFlow<T> {
    private val lock = Any()
    private var collectorState: CollectorState<T>? = null

    override suspend fun collect(collector: FlowCollector<T>) {
        val currentState = synchronized(lock) {
            collectorState?.also { ++it.refCount }
                ?: CollectorState(
                    source,
                    replay,
                    extraBufferCapacity,
                    onBufferOverflow
                ).also { collectorState = it }
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
        get() = synchronized(lock) {
            collectorState?.replayCache ?: emptyList()
        }

    override fun fuse(
        context: CoroutineContext,
        capacity: Int,
        onBufferOverflow: BufferOverflow
    ): Flow<T> {
        if (capacity == Channel.RENDEZVOUS) {
            return this
        }
        return RefCountSharedFlow(source, replay, capacity, onBufferOverflow)
    }

    private class CollectorState<T>(
        source: Flow<T>,
        private val replay: Int,
        extraBufferCapacity: Int,
        onBufferOverflow: BufferOverflow
    ) {
        private val sharedFlow =
            MutableSharedFlow<Any?>(0, extraBufferCapacity, onBufferOverflow)

        private val replayMutex by lazy { Mutex() }
        private val replayBuffer by lazy { ArrayDeque<T>(replay) }

        @Volatile
        private var terminal: Any? = null

        private val job = GlobalScope.launch(start = CoroutineStart.LAZY) {
            try {
                source.collect {
                    if (replay > 0) {
                        replayMutex.withLock {
                            synchronized(replayBuffer) {
                                while (replayBuffer.size >= replay) {
                                    replayBuffer.removeFirst()
                                }
                                replayBuffer.addLast(it)
                            }
                            sharedFlow.emit(it)
                        }
                    } else {
                        sharedFlow.emit(it)
                    }
                }
                terminal = Completed
                sharedFlow.emit(Completed)
            } catch (e: CollectorDisposedException) {
                // disposed
            } catch (e: Throwable) {
                val exceptionTerminal = CompletedWithException(e)
                terminal = exceptionTerminal
                sharedFlow.emit(exceptionTerminal)
            }
        }

        @Volatile
        var refCount = 1

        val replayCache: List<T>
            get() = if (replay > 0) {
                synchronized(replayBuffer) { replayBuffer.toList() }
            } else {
                emptyList()
            }

        fun dispose() {
            job.cancel(CollectorDisposedException())
        }

        suspend fun collect(collector: FlowCollector<T>) {
            var replayMutexOwner: Any? = null
            if (replay > 0) {
                replayMutexOwner = Any()
                replayMutex.lock(replayMutexOwner)
            }
            try {
                sharedFlow
                    .onSubscription {
                        replayMutexOwner?.let { lockOwner ->
                            synchronized(replayBuffer) { replayBuffer.toList() }.also {
                                runCatching { replayMutex.unlock(lockOwner) }
                                replayMutexOwner = null
                            }
                        }?.forEach { emit(it) }
                        terminal?.let { emit(it) }
                        job.start()
                    }
                    .takeWhile { it !is Completed }
                    .collect {
                        if (it is CompletedWithException) {
                            throw it.e
                        } else {
                            collector.emit(it as T)
                        }
                    }
            } finally {
                replayMutexOwner?.let {
                    runCatching { replayMutex.unlock(it) }
                }
            }
        }
    }

    private object Completed

    private class CompletedWithException(val e: Throwable)
}