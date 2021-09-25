package com.github.skgmn.coroutineutils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.internal.FusibleFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> Flow<T>.shareRefCount(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): RefCountSharedFlow<T> {
    return RefCountSharedFlow(
        this,
        EmptyCoroutineContext,
        replay,
        extraBufferCapacity,
        onBufferOverflow
    )
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class, DelicateCoroutinesApi::class)
class RefCountSharedFlow<T> internal constructor(
    private val source: Flow<T>,
    private val context: CoroutineContext,
    private val replay: Int,
    private val extraBufferCapacity: Int,
    private val onBufferOverflow: BufferOverflow
) : SharedFlow<T>, FusibleFlow<T> {
    private val lock inline get() = sharedFlow
    private var collectorState: CollectorState<T>? = null
    private val sharedFlow = MutableSharedFlow<T>(replay, extraBufferCapacity, onBufferOverflow)

    override suspend fun collect(collector: FlowCollector<T>) {
        val currentState = synchronized(lock) {
            collectorState?.also { ++it.refCount }
                ?: CollectorState(source, context, sharedFlow).also { collectorState = it }
        }
        try {
            coroutineScope {
                val outerScope = this
                launch {
                    val terminal = currentState.getTerminal()
                    if (terminal is CancellationException) {
                        outerScope.cancel(terminal)
                    } else if (terminal is Throwable) {
                        throw terminal
                    } else {
                        outerScope.cancel(CompletedException())
                    }
                }
                sharedFlow.onSubscription { currentState.start() }.collect(collector)
            }
        } catch (e: CompletedException) {
            // completed
        } finally {
            synchronized(lock) {
                if (--currentState.refCount == 0) {
                    currentState.cancel()
                    collectorState = null
                    sharedFlow.resetReplayCache()
                }
            }
        }
    }

    override val replayCache: List<T>
        get() = sharedFlow.replayCache

    override fun fuse(
        context: CoroutineContext,
        capacity: Int,
        onBufferOverflow: BufferOverflow
    ): Flow<T> {
        if (this.context == context &&
            this.extraBufferCapacity == capacity &&
            this.onBufferOverflow == onBufferOverflow
        ) {
            return this
        }
        this.conflate()
        return RefCountSharedFlow(source, context, replay, capacity, onBufferOverflow)
    }

    fun flowOn(context: CoroutineContext): RefCountSharedFlow<T> {
        return RefCountSharedFlow(source, context, replay, extraBufferCapacity, onBufferOverflow)
    }

    fun buffer(
        capacity: Int = Channel.BUFFERED,
        onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
    ): RefCountSharedFlow<T> {
        return if (this.extraBufferCapacity == capacity &&
            this.onBufferOverflow == onBufferOverflow
        ) {
            this
        } else {
            RefCountSharedFlow(source, context, replay, capacity, onBufferOverflow)
        }
    }

    fun conflate(): RefCountSharedFlow<T> {
        return buffer(Channel.CONFLATED)
    }

    private class CollectorState<T>(
        source: Flow<T>,
        context: CoroutineContext,
        sharedFlow: MutableSharedFlow<T>,
        private val terminal: MutableStateFlow<Any?> = MutableStateFlow(null),
        private val job: Job = GlobalScope.launch(context, CoroutineStart.LAZY) {
            try {
                source.collect(sharedFlow)
                terminal.value = Unit
            } catch (e: Throwable) {
                terminal.value = e
            }
        }
    ) {
        var refCount = 1

        fun start() {
            job.start()
        }

        fun cancel() {
            job.cancel()
        }

        suspend fun getTerminal(): Any {
            return checkNotNull(terminal.filterNotNull().firstOrNull())
        }
    }

    private class CompletedException : CancellationException() {
        override fun fillInStackTrace(): Throwable {
            stackTrace = emptyArray()
            return this
        }
    }
}